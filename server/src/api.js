/**
 * HTTP layer for the SAMVIRA backend (Milestone 0.3 endpoints).
 *
 * Exposes the versioned contract under `/api/v1`. The server is the source of
 * truth for authorization: it validates challenges, verifies signatures, and
 * issues sessions. It never trusts client-provided authorization claims.
 *
 * Access logs record only the method, path (no query string), status, and
 * duration — never headers, tokens, or bodies.
 */
import http from 'node:http';
import { createPublicKey } from 'node:crypto';
import { ApiError } from './errors.js';
import {
  fromB64url,
  hashToken,
  newChallengeId,
  newNonce,
  newSessionToken,
  toB64url,
  verifySignature,
} from './security.js';

function sendJson(res, status, body) {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': Buffer.byteLength(payload),
  });
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
    return JSON.parse(raw);
  } catch {
    throw ApiError.malformed('request body is not valid JSON');
  }
}

function requireString(body, field) {
  const value = body[field];
  if (typeof value !== 'string' || value.trim() === '') {
    throw ApiError.malformed(`field "${field}" is required and must be a non-empty string`);
  }
  return value;
}

function validatePublicKey(pem) {
  try {
    createPublicKey(pem);
  } catch {
    throw ApiError.malformed('public_key_pem is not a valid public key');
  }
}

function authorizeSession(storage, req, nowMs) {
  const header = req.headers.authorization;
  if (typeof header !== 'string' || !header.startsWith('Bearer ')) {
    throw ApiError.unauthorized('SESSION_INVALID', 'missing or malformed authorization header');
  }
  const token = header.slice('Bearer '.length).trim();
  if (token.length === 0) {
    throw ApiError.unauthorized('SESSION_INVALID', 'missing bearer token');
  }
  const session = storage.getSession(hashToken(token));
  if (!session) {
    throw ApiError.unauthorized('SESSION_INVALID', 'unknown session');
  }
  if (session.revoked_at != null) {
    throw ApiError.unauthorized('SESSION_REVOKED', 'session has been revoked');
  }
  if (nowMs >= session.expires_at) {
    throw ApiError.unauthorized('SESSION_EXPIRED', 'session has expired');
  }
  return session;
}

export function createServer({ storage, config, now = () => Date.now(), log = () => {} }) {
  const server = http.createServer(async (req, res) => {
    const started = process.hrtime.bigint();
    try {
      await dispatch(req, res);
    } catch (err) {
      handleError(res, err, log);
    } finally {
      const path = safePath(req.url);
      const elapsedMs = Number(process.hrtime.bigint() - started) / 1e6;
      log(`[api] ${req.method} ${path} ${res.statusCode} ${elapsedMs.toFixed(1)}ms`);
    }
  });

  async function dispatch(req, res) {
    const url = new URL(req.url, 'http://localhost');
    const method = req.method || 'GET';
    const path = url.pathname;

    if (method === 'GET' && path === '/healthz') {
      return sendJson(res, 200, { status: 'ok' });
    }

    if (method === 'POST' && path === '/api/v1/installations/register') {
      const body = await readJsonBody(req, config.bodyLimitBytes);
      const installationId = requireString(body, 'installation_id');
      const publicKeyPem = requireString(body, 'public_key_pem');
      validatePublicKey(publicKeyPem);
      const record = storage.registerInstallation(installationId, publicKeyPem, now());
      return sendJson(res, 200, record);
    }

    if (method === 'POST' && path === '/api/v1/installations/challenge') {
      const body = await readJsonBody(req, config.bodyLimitBytes);
      const installationId = requireString(body, 'installation_id');
      const installation = storage.getInstallation(installationId);
      if (!installation) {
        throw ApiError.notFound('UNKNOWN_INSTALLATION', 'installation is not registered');
      }
      const challengeId = newChallengeId();
      const nonce = newNonce();
      const expiresAt = now() + config.challengeTtlMs;
      storage.createChallenge({
        challengeId,
        installationId,
        nonceB64: toB64url(nonce),
        nonce,
        expiresAt,
      });
      return sendJson(res, 200, {
        challenge_id: challengeId,
        nonce_b64: toB64url(nonce),
        expires_at_epoch_ms: expiresAt,
      });
    }

    if (method === 'POST' && path === '/api/v1/installations/verify') {
      const body = await readJsonBody(req, config.bodyLimitBytes);
      const installationId = requireString(body, 'installation_id');
      const challengeId = requireString(body, 'challenge_id');
      const signatureB64 = requireString(body, 'signature_b64');

      const challenge = storage.getChallenge(challengeId);
      if (!challenge) {
        throw ApiError.notFound('UNKNOWN_CHALLENGE', 'challenge does not exist');
      }
      if (challenge.installation_id !== installationId) {
        throw ApiError.forbidden('CHALLENGE_MISMATCH', 'challenge was not issued for this installation');
      }
      if (challenge.used_at != null) {
        throw ApiError.conflict('CHALLENGE_REPLAYED', 'challenge has already been used');
      }
      if (now() >= challenge.expires_at) {
        throw ApiError.gone('CHALLENGE_EXPIRED', 'challenge has expired');
      }

      const installation = storage.getInstallation(installationId);
      if (!installation) {
        throw ApiError.notFound('UNKNOWN_INSTALLATION', 'installation is not registered');
      }

      let signature;
      try {
        signature = fromB64url(signatureB64);
      } catch {
        throw ApiError.malformed('signature_b64 is not valid base64url');
      }

      const valid = verifySignature({
        publicKeyPem: installation.public_key_pem,
        data: challenge.nonce,
        signature,
      });
      if (!valid) {
        throw ApiError.unauthorized('INVALID_SIGNATURE', 'signature does not match the registered public key');
      }

      // Single-use: mark the challenge consumed only after the signature passes.
      storage.markChallengeUsed(challengeId, now());

      const token = newSessionToken();
      const expiresAt = now() + config.sessionTtlMs;
      storage.createSession({
        tokenHash: hashToken(token),
        installationId,
        createdAt: now(),
        expiresAt,
      });

      return sendJson(res, 200, {
        installation_id: installationId,
        session_token: token,
        session_expires_at_epoch_ms: expiresAt,
      });
    }

    if (method === 'POST' && path === '/api/v1/session/revoke') {
      const session = authorizeSession(storage, req, now());
      storage.revokeSession(session.token_hash, now());
      return sendJson(res, 200, { revoked: true });
    }

    // --- organizations (Milestone 0.4) ---

    if (method === 'GET' && path === '/api/v1/organizations') {
      const session = authorizeSession(storage, req, now());
      const memberships = storage.listMemberships(session.installation_id);
      return sendJson(res, 200, {
        organizations: memberships.map((m) => ({
          organization_id: m.organization_id,
          name: m.name,
          state: m.state,
        })),
      });
    }

    const orgContextMatch = path.match(/^\/api\/v1\/organizations\/([^/]+)\/context$/);
    if (method === 'GET' && orgContextMatch) {
      const session = authorizeSession(storage, req, now());
      const organizationId = decodeURIComponent(orgContextMatch[1]);
      // The client must state its organization context explicitly and it must
      // match the resource being accessed. This is the cross-organization
      // isolation boundary.
      const headerOrg = req.headers['x-organization-id'];
      if (typeof headerOrg !== 'string' || headerOrg.trim() === '') {
        throw new ApiError(400, 'ORG_CONTEXT_INVALID', 'missing X-Organization-Id header');
      }
      if (headerOrg.trim() !== organizationId) {
        throw new ApiError(400, 'ORG_CONTEXT_INVALID', 'X-Organization-Id does not match the requested organization');
      }
      const org = storage.getOrganization(organizationId);
      if (!org) {
        throw ApiError.notFound('UNKNOWN_ORGANIZATION', 'organization does not exist');
      }
      const membership = storage.getMembership(session.installation_id, organizationId);
      if (!membership) {
        throw ApiError.forbidden('NOT_A_MEMBER', 'installation is not a member of this organization');
      }
      if (membership.state !== 'ACTIVE') {
        throw ApiError.forbidden('MEMBERSHIP_NOT_ACTIVE', 'membership is not active');
      }
      return sendJson(res, 200, {
        organization_id: org.organization_id,
        name: org.name,
        state: membership.state,
      });
    }

    throw ApiError.notFound('UNKNOWN_ROUTE', 'route not found');
  }

  return server;
}

function safePath(rawUrl) {
  try {
    return new URL(rawUrl, 'http://localhost').pathname;
  } catch {
    return '/';
  }
}

function handleError(res, err, log) {
  if (err instanceof ApiError) {
    sendJson(res, err.status, err.toBody());
    return;
  }
  log(`[api] internal error: ${err && err.message ? err.message : err}`);
  sendJson(res, 500, { error: { code: 'INTERNAL_ERROR', message: 'internal server error' } });
}
