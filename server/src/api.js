/**
 * HTTP layer for the SAMVIRA backend (Milestone 0.3 endpoints).
 *
 * The server is the source of truth for authorization. Access logs never
 * include headers, tokens, query strings, or request bodies.
 */
import http from 'node:http';
import { createPublicKey } from 'node:crypto';
import { ApiError } from './errors.js';
import { fromB64url, hashToken, newChallengeId, newNonce, newSessionToken, toB64url, verifySignature } from './security.js';

function sendJson(res, status, body) {
  const payload = JSON.stringify(body);
  res.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'content-length': Buffer.byteLength(payload) });
  res.end(payload);
}

async function readJsonBody(req, limitBytes) {
  const chunks = [];
  let total = 0;
  for await (const chunk of req) {
    total += chunk.length;
    if (total > limitBytes) throw ApiError.malformed('request body too large');
    chunks.push(chunk);
  }
  const raw = Buffer.concat(chunks).toString('utf8');
  if (raw.trim().length === 0) return {};
  try {
    const parsed = JSON.parse(raw);
    if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
      throw ApiError.malformed('request body must be a JSON object');
    }
    return parsed;
  } catch (err) {
    if (err instanceof ApiError) throw err;
    throw ApiError.malformed('request body is not valid JSON');
  }
}

function requireString(body, field) {
  const value = body[field];
  if (typeof value !== 'string' || value.trim() === '') throw ApiError.malformed(`field "${field}" is required and must be a non-empty string`);
  if (value.length > 4096) throw ApiError.malformed(`field "${field}" is too long`);
  return value;
}

function validatePublicKey(pem) {
  try {
    const key = createPublicKey(pem);
    if (key.type !== 'public') throw new Error('private key supplied');
    return key.export({ type: 'spki', format: 'pem' });
  } catch {
    throw ApiError.malformed('public_key_pem must be a valid public key');
  }
}

function authorizeSession(storage, req, nowMs) {
  const header = req.headers.authorization;
  if (typeof header !== 'string' || !header.startsWith('Bearer ')) throw ApiError.unauthorized('SESSION_INVALID', 'missing or malformed authorization header');
  const token = header.slice('Bearer '.length).trim();
  if (token.length === 0) throw ApiError.unauthorized('SESSION_INVALID', 'missing bearer token');
  const session = storage.getSession(hashToken(token));
  if (!session) throw ApiError.unauthorized('SESSION_INVALID', 'unknown session');
  if (session.revoked_at != null) throw ApiError.unauthorized('SESSION_REVOKED', 'session has been revoked');
  if (nowMs >= session.expires_at) throw ApiError.unauthorized('SESSION_EXPIRED', 'session has expired');
  return session;
}

export function createServer({ storage, config, now = () => Date.now(), log = () => {} }) {
  const rateBuckets = new Map();

  function rateLimit(req, key, limit) {
    const ip = req.socket.remoteAddress || 'unknown';
    const bucketKey = `${key}:${ip}`;
    const current = now();
    const bucket = rateBuckets.get(bucketKey);
    if (!bucket || current - bucket.startedAt >= config.rateLimitWindowMs) {
      rateBuckets.set(bucketKey, { startedAt: current, count: 1 });
      return;
    }
    if (bucket.count >= limit) throw new ApiError(429, 'RATE_LIMITED', 'too many requests');
    bucket.count += 1;
  }

  const server = http.createServer(async (req, res) => {
    const started = process.hrtime.bigint();
    try { await dispatch(req, res); }
    catch (err) { handleError(res, err, log); }
    finally {
      const path = safePath(req.url);
      const elapsedMs = Number(process.hrtime.bigint() - started) / 1e6;
      log(`[api] ${req.method} ${path} ${res.statusCode} ${elapsedMs.toFixed(1)}ms`);
    }
  });

  async function dispatch(req, res) {
    const url = new URL(req.url, 'http://localhost');
    const method = req.method || 'GET';
    const path = url.pathname;

    if (method === 'GET' && path === '/healthz') return sendJson(res, 200, { status: 'ok' });

    if (method === 'POST' && path === '/api/v1/installations/register') {
      rateLimit(req, 'register', config.registrationRequestsPerWindow);
      const body = await readJsonBody(req, config.bodyLimitBytes);
      const installationId = requireString(body, 'installation_id');
      const canonicalPublicKey = validatePublicKey(requireString(body, 'public_key_pem'));
      const record = storage.registerInstallation(installationId, canonicalPublicKey, now());
      return sendJson(res, 200, record);
    }

    if (method === 'POST' && path === '/api/v1/installations/challenge') {
      rateLimit(req, 'challenge', config.challengeRequestsPerWindow);
      const body = await readJsonBody(req, config.bodyLimitBytes);
      const installationId = requireString(body, 'installation_id');
      const installation = storage.getInstallation(installationId);
      if (!installation) throw ApiError.notFound('UNKNOWN_INSTALLATION', 'installation is not registered');
      const challengeId = newChallengeId();
      const nonce = newNonce();
      const expiresAt = now() + config.challengeTtlMs;
      storage.createChallenge({ challengeId, installationId, nonceB64: toB64url(nonce), nonce, expiresAt, nowMs: now() });
      return sendJson(res, 200, { challenge_id: challengeId, nonce_b64: toB64url(nonce), expires_at_epoch_ms: expiresAt });
    }

    if (method === 'POST' && path === '/api/v1/installations/verify') {
      const body = await readJsonBody(req, config.bodyLimitBytes);
      const installationId = requireString(body, 'installation_id');
      const challengeId = requireString(body, 'challenge_id');
      const signatureB64 = requireString(body, 'signature_b64');
      const challenge = storage.getChallenge(challengeId);
      if (!challenge) throw ApiError.notFound('UNKNOWN_CHALLENGE', 'challenge does not exist');
      if (challenge.installation_id !== installationId) throw ApiError.forbidden('CHALLENGE_MISMATCH', 'challenge was not issued for this installation');
      if (challenge.used_at != null) throw ApiError.conflict('CHALLENGE_REPLAYED', 'challenge has already been used');
      const currentTime = now();
      if (currentTime >= challenge.expires_at) throw ApiError.gone('CHALLENGE_EXPIRED', 'challenge has expired');
      const installation = storage.getInstallation(installationId);
      if (!installation) throw ApiError.notFound('UNKNOWN_INSTALLATION', 'installation is not registered');
      let signature;
      try { signature = fromB64url(signatureB64); }
      catch { throw ApiError.malformed('signature_b64 is not valid base64url'); }
      if (!verifySignature({ publicKeyPem: installation.public_key_pem, data: challenge.nonce, signature })) {
        throw ApiError.unauthorized('INVALID_SIGNATURE', 'signature does not match the registered public key');
      }
      const token = newSessionToken();
      const createdAt = now();
      const expiresAt = createdAt + config.sessionTtlMs;
      const consumed = storage.consumeChallengeAndCreateSession({
        challengeId, nowMs: currentTime, tokenHash: hashToken(token), installationId, createdAt, expiresAt,
      });
      if (!consumed) throw ApiError.conflict('CHALLENGE_REPLAYED', 'challenge has already been used or expired');
      return sendJson(res, 200, { installation_id: installationId, session_token: token, session_expires_at_epoch_ms: expiresAt });
    }

    if (method === 'POST' && path === '/api/v1/session/revoke') {
      const session = authorizeSession(storage, req, now());
      storage.revokeSession(session.token_hash, now());
      return sendJson(res, 200, { revoked: true });
    }

    if (method === 'GET' && path === '/api/v1/organizations') {
      const session = authorizeSession(storage, req, now());
      const memberships = storage.listMemberships(session.installation_id);
      return sendJson(res, 200, { organizations: memberships.map((m) => ({ organization_id: m.organization_id, name: m.name, state: m.state })) });
    }

    const orgContextMatch = path.match(/^\/api\/v1\/organizations\/([^/]+)\/context$/);
    if (method === 'GET' && orgContextMatch) {
      const session = authorizeSession(storage, req, now());
      let organizationId;
      try { organizationId = decodeURIComponent(orgContextMatch[1]); }
      catch { throw ApiError.malformed('organization id is not valid URL encoding'); }
      const headerOrg = req.headers['x-organization-id'];
      if (typeof headerOrg !== 'string' || headerOrg.trim() === '') throw new ApiError(400, 'ORG_CONTEXT_INVALID', 'missing X-Organization-Id header');
      if (headerOrg.trim() !== organizationId) throw new ApiError(400, 'ORG_CONTEXT_INVALID', 'X-Organization-Id does not match the requested organization');
      const org = storage.getOrganization(organizationId);
      if (!org) throw ApiError.notFound('UNKNOWN_ORGANIZATION', 'organization does not exist');
      const membership = storage.getMembership(session.installation_id, organizationId);
      if (!membership) throw ApiError.forbidden('NOT_A_MEMBER', 'installation is not a member of this organization');
      if (membership.state !== 'ACTIVE') throw ApiError.forbidden('MEMBERSHIP_NOT_ACTIVE', 'membership is not active');
      return sendJson(res, 200, { organization_id: org.organization_id, name: org.name, state: membership.state });
    }

    throw ApiError.notFound('UNKNOWN_ROUTE', 'route not found');
  }

  return server;
}

function safePath(rawUrl) {
  try { return new URL(rawUrl, 'http://localhost').pathname; } catch { return '/'; }
}

function handleError(res, err, log) {
  if (err instanceof ApiError) { sendJson(res, err.status, err.toBody()); return; }
  log(`[api] internal error: ${err && err.message ? err.message : err}`);
  sendJson(res, 500, { error: { code: 'INTERNAL_ERROR', message: 'internal server error' } });
}
