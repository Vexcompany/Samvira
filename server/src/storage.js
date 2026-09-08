/**
 * Persistence layer (SQLite via the built-in `node:sqlite` module).
 *
 * All server state — installation records, challenges, and sessions — lives
 * here behind plain functions, so the storage engine can be replaced without
 * touching the HTTP layer. Session tokens are stored hashed; the raw token is
 * never persisted.
 *
 * `node:sqlite` is experimental in Node 22 but is the only zero-dependency
 * SQLite option; it is isolated to this single module.
 */
import { DatabaseSync } from 'node:sqlite';
import { ApiError } from './errors.js';

const SCHEMA = `
CREATE TABLE IF NOT EXISTS installations (
  installation_id TEXT PRIMARY KEY,
  public_key_pem  TEXT NOT NULL,
  registered_at   INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS challenges (
  challenge_id    TEXT PRIMARY KEY,
  installation_id TEXT NOT NULL,
  nonce_b64       TEXT NOT NULL,
  nonce           BLOB NOT NULL,
  expires_at      INTEGER NOT NULL,
  used_at         INTEGER
);
CREATE TABLE IF NOT EXISTS sessions (
  token_hash      TEXT PRIMARY KEY,
  installation_id TEXT NOT NULL,
  created_at      INTEGER NOT NULL,
  expires_at      INTEGER NOT NULL,
  revoked_at      INTEGER
);
CREATE TABLE IF NOT EXISTS organizations (
  organization_id TEXT PRIMARY KEY,
  name            TEXT NOT NULL,
  created_at      INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS memberships (
  installation_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  state           TEXT NOT NULL,
  created_at      INTEGER NOT NULL,
  updated_at      INTEGER NOT NULL,
  PRIMARY KEY (installation_id, organization_id)
);
`;

export function createStorage(dbPath = ':memory:') {
  const db = new DatabaseSync(dbPath);
  db.exec(SCHEMA);

  return {
    // --- installations ---

    registerInstallation(installationId, publicKeyPem, nowMs) {
      const existing = db
        .prepare('SELECT installation_id, public_key_pem, registered_at FROM installations WHERE installation_id = ?')
        .get(installationId);
      if (existing) {
        if (existing.public_key_pem === publicKeyPem) {
          return {
            installation_id: installationId,
            registered_at: existing.registered_at,
            already_registered: true,
          };
        }
        throw ApiError.conflict(
          'INSTALLATION_CONFLICT',
          'installation id is already registered with a different public key',
        );
      }
      db.prepare(
        'INSERT INTO installations (installation_id, public_key_pem, registered_at) VALUES (?, ?, ?)',
      ).run(installationId, publicKeyPem, nowMs);
      return { installation_id: installationId, registered_at: nowMs, already_registered: false };
    },

    getInstallation(installationId) {
      return db
        .prepare('SELECT installation_id, public_key_pem, registered_at FROM installations WHERE installation_id = ?')
        .get(installationId);
    },

    // --- challenges ---

    createChallenge({ challengeId, installationId, nonceB64, nonce, expiresAt }) {
      db.prepare(
        'INSERT INTO challenges (challenge_id, installation_id, nonce_b64, nonce, expires_at) VALUES (?, ?, ?, ?, ?)',
      ).run(challengeId, installationId, nonceB64, nonce, expiresAt);
    },

    getChallenge(challengeId) {
      return db.prepare('SELECT * FROM challenges WHERE challenge_id = ?').get(challengeId);
    },

    markChallengeUsed(challengeId, nowMs) {
      db.prepare('UPDATE challenges SET used_at = ? WHERE challenge_id = ? AND used_at IS NULL')
        .run(nowMs, challengeId);
    },

    // --- sessions ---

    createSession({ tokenHash, installationId, createdAt, expiresAt }) {
      db.prepare(
        'INSERT INTO sessions (token_hash, installation_id, created_at, expires_at) VALUES (?, ?, ?, ?)',
      ).run(tokenHash, installationId, createdAt, expiresAt);
    },

    getSession(tokenHash) {
      return db.prepare('SELECT * FROM sessions WHERE token_hash = ?').get(tokenHash);
    },

    revokeSession(tokenHash, nowMs) {
      db.prepare('UPDATE sessions SET revoked_at = ? WHERE token_hash = ?').run(nowMs, tokenHash);
    },

    // --- organizations ---

    createOrganization(organizationId, name, nowMs) {
      const existing = db
        .prepare('SELECT organization_id, name, created_at FROM organizations WHERE organization_id = ?')
        .get(organizationId);
      if (existing) {
        return {
          organization_id: organizationId,
          name: existing.name,
          created_at: existing.created_at,
          already_exists: true,
        };
      }
      db.prepare('INSERT INTO organizations (organization_id, name, created_at) VALUES (?, ?, ?)')
        .run(organizationId, name, nowMs);
      return { organization_id: organizationId, name, created_at: nowMs, already_exists: false };
    },

    getOrganization(organizationId) {
      return db.prepare('SELECT * FROM organizations WHERE organization_id = ?').get(organizationId);
    },

    // --- memberships ---

    /**
     * Adds or updates a membership. Enforces the server-side maximum number of
     * organizations per installation (default 2) on new memberships.
     */
    addMembership(installationId, organizationId, state, nowMs, maxOrgs) {
      const org = this.getOrganization(organizationId);
      if (!org) {
        throw ApiError.notFound('UNKNOWN_ORGANIZATION', 'organization does not exist');
      }
      const existing = db
        .prepare('SELECT * FROM memberships WHERE installation_id = ? AND organization_id = ?')
        .get(installationId, organizationId);
      if (existing) {
        db.prepare(
          'UPDATE memberships SET state = ?, updated_at = ? WHERE installation_id = ? AND organization_id = ?',
        ).run(state, nowMs, installationId, organizationId);
        return { installation_id: installationId, organization_id: organizationId, state };
      }
      const count = this.countMemberships(installationId);
      if (count >= maxOrgs) {
        throw new ApiError(
          409,
          'ORG_LIMIT_EXCEEDED',
          'installation already belongs to the maximum number of organizations',
        );
      }
      db.prepare(
        'INSERT INTO memberships (installation_id, organization_id, state, created_at, updated_at) VALUES (?, ?, ?, ?, ?)',
      ).run(installationId, organizationId, state, nowMs, nowMs);
      return { installation_id: installationId, organization_id: organizationId, state };
    },

    getMembership(installationId, organizationId) {
      return db
        .prepare('SELECT * FROM memberships WHERE installation_id = ? AND organization_id = ?')
        .get(installationId, organizationId);
    },

    setMembershipState(installationId, organizationId, state, nowMs) {
      const result = db
        .prepare('UPDATE memberships SET state = ?, updated_at = ? WHERE installation_id = ? AND organization_id = ?')
        .run(state, nowMs, installationId, organizationId);
      return result.changes > 0;
    },

    countMemberships(installationId) {
      const row = db
        .prepare('SELECT COUNT(*) AS c FROM memberships WHERE installation_id = ?')
        .get(installationId);
      return row.c;
    },

    listMemberships(installationId) {
      return db
        .prepare(
          `SELECT m.organization_id, o.name, m.state, m.created_at, m.updated_at
           FROM memberships m
           JOIN organizations o ON o.organization_id = m.organization_id
           WHERE m.installation_id = ?
           ORDER BY m.created_at ASC`,
        )
        .all(installationId);
    },

    close() {
      db.close();
    },
  };
}
