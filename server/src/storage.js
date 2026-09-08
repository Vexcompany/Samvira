/** SQLite persistence boundary for SAMVIRA server state. */
import { DatabaseSync } from 'node:sqlite';
import { ApiError } from './errors.js';

const SCHEMA = `
CREATE TABLE IF NOT EXISTS installations (installation_id TEXT PRIMARY KEY, public_key_pem TEXT NOT NULL, registered_at INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS challenges (challenge_id TEXT PRIMARY KEY, installation_id TEXT NOT NULL, nonce_b64 TEXT NOT NULL, nonce BLOB NOT NULL, expires_at INTEGER NOT NULL, used_at INTEGER);
CREATE INDEX IF NOT EXISTS idx_challenges_installation ON challenges(installation_id);
CREATE INDEX IF NOT EXISTS idx_challenges_expiry ON challenges(expires_at);
CREATE TABLE IF NOT EXISTS sessions (token_hash TEXT PRIMARY KEY, installation_id TEXT NOT NULL, created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL, revoked_at INTEGER);
CREATE INDEX IF NOT EXISTS idx_sessions_expiry ON sessions(expires_at);
CREATE TABLE IF NOT EXISTS organizations (organization_id TEXT PRIMARY KEY, name TEXT NOT NULL, created_at INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS memberships (installation_id TEXT NOT NULL, organization_id TEXT NOT NULL, state TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY (installation_id, organization_id));
CREATE TABLE IF NOT EXISTS media_views (view_token_hash TEXT PRIMARY KEY, installation_id TEXT NOT NULL, organization_id TEXT NOT NULL, media_id TEXT NOT NULL, source_url TEXT NOT NULL, mime_type TEXT NOT NULL, expires_at INTEGER NOT NULL, created_at INTEGER NOT NULL);
CREATE INDEX IF NOT EXISTS idx_media_views_expiry ON media_views(expires_at);
CREATE INDEX IF NOT EXISTS idx_media_views_installation ON media_views(installation_id, organization_id, media_id);
`;

export function createStorage(dbPath = ':memory:', { maxOrgsPerInstallation = 2, maxInstallations = 10_000 } = {}) {
  if (!Number.isSafeInteger(maxOrgsPerInstallation) || maxOrgsPerInstallation < 1) throw new Error('maxOrgsPerInstallation must be a positive integer');
  if (!Number.isSafeInteger(maxInstallations) || maxInstallations < 1) throw new Error('maxInstallations must be a positive integer');
  const db = new DatabaseSync(dbPath);
  db.exec(SCHEMA);
  const cleanupExpired = (nowMs) => {
    db.prepare('DELETE FROM challenges WHERE expires_at <= ? OR used_at IS NOT NULL').run(nowMs);
    db.prepare('DELETE FROM sessions WHERE expires_at <= ? OR revoked_at IS NOT NULL').run(nowMs);
    db.prepare('DELETE FROM media_views WHERE expires_at <= ?').run(nowMs);
  };
  return {
    registerInstallation(installationId, publicKeyPem, nowMs) {
      const existing = db.prepare('SELECT installation_id, public_key_pem, registered_at FROM installations WHERE installation_id = ?').get(installationId);
      if (existing) {
        if (existing.public_key_pem === publicKeyPem) return { installation_id: installationId, registered_at: existing.registered_at, already_registered: true };
        throw ApiError.conflict('INSTALLATION_CONFLICT', 'installation id is already registered with a different public key');
      }
      if (db.prepare('SELECT COUNT(*) AS c FROM installations').get().c >= maxInstallations) throw new ApiError(503, 'REGISTRATION_CAPACITY_REACHED', 'installation registration capacity has been reached');
      db.prepare('INSERT INTO installations VALUES (?, ?, ?)').run(installationId, publicKeyPem, nowMs);
      return { installation_id: installationId, registered_at: nowMs, already_registered: false };
    },
    getInstallation(id) { return db.prepare('SELECT installation_id, public_key_pem, registered_at FROM installations WHERE installation_id = ?').get(id); },
    createChallenge({ challengeId, installationId, nonceB64, nonce, expiresAt, nowMs = Date.now() }) { cleanupExpired(nowMs); db.prepare('INSERT INTO challenges VALUES (?, ?, ?, ?, ?, NULL)').run(challengeId, installationId, nonceB64, nonce, expiresAt); },
    getChallenge(id) { return db.prepare('SELECT * FROM challenges WHERE challenge_id = ?').get(id); },
    consumeChallengeAndCreateSession({ challengeId, nowMs, tokenHash, installationId, createdAt, expiresAt }) {
      db.exec('BEGIN IMMEDIATE');
      try {
        const result = db.prepare('UPDATE challenges SET used_at = ? WHERE challenge_id = ? AND used_at IS NULL AND expires_at > ?').run(nowMs, challengeId, nowMs);
        if (result.changes !== 1) { db.exec('ROLLBACK'); return false; }
        db.prepare('INSERT INTO sessions VALUES (?, ?, ?, ?, NULL)').run(tokenHash, installationId, createdAt, expiresAt);
        db.exec('COMMIT'); return true;
      } catch (err) { try { db.exec('ROLLBACK'); } catch {} throw err; }
    },
    getSession(tokenHash) { return db.prepare('SELECT * FROM sessions WHERE token_hash = ?').get(tokenHash); },
    revokeSession(tokenHash, nowMs) { db.prepare('UPDATE sessions SET revoked_at = ? WHERE token_hash = ?').run(nowMs, tokenHash); },
    createOrganization(id, name, nowMs) {
      const existing = db.prepare('SELECT * FROM organizations WHERE organization_id = ?').get(id);
      if (existing) return { organization_id: id, name: existing.name, created_at: existing.created_at, already_exists: true };
      db.prepare('INSERT INTO organizations VALUES (?, ?, ?)').run(id, name, nowMs); return { organization_id: id, name, created_at: nowMs, already_exists: false };
    },
    getOrganization(id) { return db.prepare('SELECT * FROM organizations WHERE organization_id = ?').get(id); },
    addMembership(installationId, organizationId, state, nowMs) {
      if (!this.getOrganization(organizationId)) throw ApiError.notFound('UNKNOWN_ORGANIZATION', 'organization does not exist');
      const existing = db.prepare('SELECT * FROM memberships WHERE installation_id = ? AND organization_id = ?').get(installationId, organizationId);
      if (existing) { db.prepare('UPDATE memberships SET state = ?, updated_at = ? WHERE installation_id = ? AND organization_id = ?').run(state, nowMs, installationId, organizationId); return { installation_id: installationId, organization_id: organizationId, state }; }
      if (this.countMemberships(installationId) >= maxOrgsPerInstallation) throw new ApiError(409, 'ORG_LIMIT_EXCEEDED', 'installation already belongs to the maximum number of organizations');
      db.prepare('INSERT INTO memberships VALUES (?, ?, ?, ?, ?)').run(installationId, organizationId, state, nowMs, nowMs); return { installation_id: installationId, organization_id: organizationId, state };
    },
    getMembership(i, o) { return db.prepare('SELECT * FROM memberships WHERE installation_id = ? AND organization_id = ?').get(i, o); },
    setMembershipState(i, o, state, nowMs) { return db.prepare('UPDATE memberships SET state = ?, updated_at = ? WHERE installation_id = ? AND organization_id = ?').run(state, nowMs, i, o).changes > 0; },
    countMemberships(i) { return db.prepare('SELECT COUNT(*) AS c FROM memberships WHERE installation_id = ?').get(i).c; },
    listMemberships(i) { return db.prepare('SELECT m.organization_id, o.name, m.state, m.created_at, m.updated_at FROM memberships m JOIN organizations o ON o.organization_id = m.organization_id WHERE m.installation_id = ? ORDER BY m.created_at ASC').all(i); },
    createMediaView({ viewTokenHash, installationId, organizationId, mediaId, sourceUrl, mimeType, expiresAt, createdAt }) { cleanupExpired(createdAt); db.prepare('INSERT INTO media_views VALUES (?, ?, ?, ?, ?, ?, ?, ?)').run(viewTokenHash, installationId, organizationId, mediaId, sourceUrl, mimeType, expiresAt, createdAt); },
    getMediaView(hash, nowMs) { const view = db.prepare('SELECT * FROM media_views WHERE view_token_hash = ? AND expires_at > ?').get(hash, nowMs); return view || null; },
    cleanupExpired,
    close() { db.close(); },
  };
}
