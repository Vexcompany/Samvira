/** HTTP layer for the SAMVIRA backend. Provider URLs never leave this boundary. */
import http from 'node:http';
import { pipeline } from 'node:stream/promises';
import { Readable } from 'node:stream';
import { createPrivateKey, createPublicKey } from 'node:crypto';
import { ApiError } from './errors.js';
import { fromB64url, hashToken, newChallengeId, newNonce, newSessionToken, toB64url, verifySignature } from './security.js';

function sendJson(res, status, body) {
  const payload = JSON.stringify(body);
  res.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'content-length': Buffer.byteLength(payload) });
  res.end(payload);
}

async function readJsonBody(req, limitBytes) {
  const chunks = []; let total = 0;
  for await (const chunk of req) { total += chunk.length; if (total > limitBytes) throw ApiError.malformed('request body too large'); chunks.push(chunk); }
  const raw = Buffer.concat(chunks).toString('utf8');
  if (!raw.trim()) return {};
  try {
    const parsed = JSON.parse(raw);
    if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) throw ApiError.malformed('request body must be a JSON object');
    return parsed;
  } catch (err) { if (err instanceof ApiError) throw err; throw ApiError.malformed('request body is not valid JSON'); }
}

function requireString(body, field) {
  const value = body[field];
  if (typeof value !== 'string' || value.trim() === '') throw ApiError.malformed(`field "${field}" is required and must be a non-empty string`);
  if (value.length > 4096) throw ApiError.malformed(`field "${field}" is too long`);
  return value;
}

function validatePublicKey(pem) {
  try {
    const publicKey = createPublicKey(pem);
    try { createPrivateKey(pem); throw new Error('private key supplied'); } catch (err) {
      if (err?.message === 'private key supplied') throw err;
    }
    return publicKey.export({ type: 'spki', format: 'pem' });
  } catch { throw ApiError.malformed('public_key_pem must be a valid public key'); }
}

function authorizeSession(storage, req, nowMs) {
  const header = req.headers.authorization;
  if (typeof header !== 'string' || !header.startsWith('Bearer ')) throw ApiError.unauthorized('SESSION_INVALID', 'missing or malformed authorization header');
  const token = header.slice(7).trim();
  if (!token) throw ApiError.unauthorized('SESSION_INVALID', 'missing bearer token');
  const session = storage.getSession(hashToken(token));
  if (!session) throw ApiError.unauthorized('SESSION_INVALID', 'unknown session');
  if (session.revoked_at != null) throw ApiError.unauthorized('SESSION_REVOKED', 'session has been revoked');
  if (nowMs >= session.expires_at) throw ApiError.unauthorized('SESSION_EXPIRED', 'session has expired');
  return session;
}

function requireActiveMembership(storage, installationId, organizationId) {
  const membership = storage.getMembership(installationId, organizationId);
  if (!membership) throw ApiError.forbidden('NOT_A_MEMBER', 'installation is not a member of this organization');
  if (membership.state !== 'ACTIVE') throw ApiError.forbidden('MEMBERSHIP_NOT_ACTIVE', 'membership is not active');
  return membership;
}

function requireOrganizationHeader(req, expected) {
  const header = req.headers['x-organization-id'];
  if (typeof header !== 'string' || header.trim() === '' || header.trim() !== expected) throw new ApiError(400, 'ORG_CONTEXT_INVALID', 'missing or mismatched X-Organization-Id header');
}

function isSafeMimeType(value, family = null) {
  if (typeof value !== 'string') return false;
  const pattern = family === 'image' ? /^image\/[A-Za-z0-9!#$&^_.+-]+$/ : /^(?:image|video)\/[A-Za-z0-9!#$&^_.+-]+$/;
  return pattern.test(value);
}

async function streamResponse(req, res, upstream, mimeType, log) {
  if (!isSafeMimeType(mimeType)) throw new ApiError(502, 'MEDIA_PROVIDER_ERROR', 'media provider returned an invalid content type');
  res.writeHead(200, { 'content-type': mimeType, 'cache-control': 'private, no-store' });
  const abortController = new AbortController();
  const abortRequest = () => abortController.abort();
  req.once('aborted', abortRequest);
  try {
    await pipeline(Readable.fromWeb(upstream.body), res, { signal: abortController.signal });
  } catch (err) {
    log(`[api] media stream failed: ${err && err.message ? err.message : err}`);
    if (!res.destroyed) res.destroy(err);
  } finally {
    req.off('aborted', abortRequest);
  }
}

export function createServer({ storage, config, provider = null, now = () => Date.now(), log = () => {} }) {
  const rateBuckets = new Map();
  function rateLimit(req, key, limit) {
    const ip = req.socket.remoteAddress || 'unknown'; const current = now(); const bucketKey = `${key}:${ip}`; const bucket = rateBuckets.get(bucketKey);
    if (!bucket || current - bucket.startedAt >= config.rateLimitWindowMs) { rateBuckets.set(bucketKey, { startedAt: current, count: 1 }); return; }
    if (bucket.count >= limit) throw new ApiError(429, 'RATE_LIMITED', 'too many requests'); bucket.count += 1;
  }
  const server = http.createServer(async (req, res) => {
    const started = process.hrtime.bigint();
    try { await dispatch(req, res); } catch (err) { handleError(res, err, log); }
    finally { const path = safePath(req.url); const elapsedMs = Number(process.hrtime.bigint() - started) / 1e6; log(`[api] ${req.method} ${path} ${res.statusCode} ${elapsedMs.toFixed(1)}ms`); }
  });

  async function dispatch(req, res) {
    const url = new URL(req.url, 'http://localhost'); const method = req.method || 'GET'; const path = url.pathname;
    if (method === 'GET' && path === '/healthz') return sendJson(res, 200, { status: 'ok' });

    if (method === 'POST' && path === '/api/v1/installations/register') {
      rateLimit(req, 'register', config.registrationRequestsPerWindow); const body = await readJsonBody(req, config.bodyLimitBytes);
      const installationId = requireString(body, 'installation_id'); const canonicalPublicKey = validatePublicKey(requireString(body, 'public_key_pem'));
      return sendJson(res, 200, storage.registerInstallation(installationId, canonicalPublicKey, now()));
    }
    if (method === 'POST' && path === '/api/v1/installations/challenge') {
      rateLimit(req, 'challenge', config.challengeRequestsPerWindow); const body = await readJsonBody(req, config.bodyLimitBytes); const installationId = requireString(body, 'installation_id');
      if (!storage.getInstallation(installationId)) throw ApiError.notFound('UNKNOWN_INSTALLATION', 'installation is not registered');
      const challengeId = newChallengeId(); const nonce = newNonce(); const expiresAt = now() + config.challengeTtlMs;
      storage.createChallenge({ challengeId, installationId, nonceB64: toB64url(nonce), nonce, expiresAt, nowMs: now() });
      return sendJson(res, 200, { challenge_id: challengeId, nonce_b64: toB64url(nonce), expires_at_epoch_ms: expiresAt });
    }
    if (method === 'POST' && path === '/api/v1/installations/verify') {
      const body = await readJsonBody(req, config.bodyLimitBytes); const installationId = requireString(body, 'installation_id'); const challengeId = requireString(body, 'challenge_id'); const signatureB64 = requireString(body, 'signature_b64');
      const challenge = storage.getChallenge(challengeId); if (!challenge) throw ApiError.notFound('UNKNOWN_CHALLENGE', 'challenge does not exist');
      if (challenge.installation_id !== installationId) throw ApiError.forbidden('CHALLENGE_MISMATCH', 'challenge was not issued for this installation');
      const currentTime = now(); if (challenge.used_at != null) throw ApiError.conflict('CHALLENGE_REPLAYED', 'challenge has already been used'); if (currentTime >= challenge.expires_at) throw ApiError.gone('CHALLENGE_EXPIRED', 'challenge has expired');
      const installation = storage.getInstallation(installationId); if (!installation) throw ApiError.notFound('UNKNOWN_INSTALLATION', 'installation is not registered');
      let signature; try { signature = fromB64url(signatureB64); } catch { throw ApiError.malformed('signature_b64 is not valid base64url'); }
      if (!verifySignature({ publicKeyPem: installation.public_key_pem, data: challenge.nonce, signature })) throw ApiError.unauthorized('INVALID_SIGNATURE', 'signature does not match the registered public key');
      const token = newSessionToken(); const createdAt = now(); const expiresAt = createdAt + config.sessionTtlMs;
      if (!storage.consumeChallengeAndCreateSession({ challengeId, nowMs: currentTime, tokenHash: hashToken(token), installationId, createdAt, expiresAt })) throw ApiError.conflict('CHALLENGE_REPLAYED', 'challenge has already been used or expired');
      return sendJson(res, 200, { installation_id: installationId, session_token: token, session_expires_at_epoch_ms: expiresAt });
    }
    if (method === 'POST' && path === '/api/v1/session/revoke') { const session = authorizeSession(storage, req, now()); storage.revokeSession(session.token_hash, now()); return sendJson(res, 200, { revoked: true }); }
    if (method === 'GET' && path === '/api/v1/organizations') { const session = authorizeSession(storage, req, now()); const memberships = storage.listMemberships(session.installation_id); return sendJson(res, 200, { organizations: memberships.map((m) => ({ organization_id: m.organization_id, name: m.name, state: m.state })) }); }

    const orgContextMatch = path.match(/^\/api\/v1\/organizations\/([^/]+)\/context$/);
    if (method === 'GET' && orgContextMatch) {
      const session = authorizeSession(storage, req, now()); let organizationId; try { organizationId = decodeURIComponent(orgContextMatch[1]); } catch { throw ApiError.malformed('organization id is not valid URL encoding'); }
      requireOrganizationHeader(req, organizationId); const org = storage.getOrganization(organizationId); if (!org) throw ApiError.notFound('UNKNOWN_ORGANIZATION', 'organization does not exist'); const membership = requireActiveMembership(storage, session.installation_id, organizationId);
      return sendJson(res, 200, { organization_id: org.organization_id, name: org.name, state: membership.state });
    }

    const mediaMatch = path.match(/^\/api\/v1\/media\/([^/]+)\/(view|content|thumbnail)$/);
    if (method === 'GET' && path === '/api/v1/media') {
      const session = authorizeSession(storage, req, now()); const organizationId = url.searchParams.get('organization_id');
      if (!organizationId) throw ApiError.malformed('organization_id is required'); requireOrganizationHeader(req, organizationId); requireActiveMembership(storage, session.installation_id, organizationId);
      if (!provider) throw new ApiError(503, 'MEDIA_PROVIDER_UNAVAILABLE', 'Pagaska Drive provider is not configured');
      const items = await provider.listMedia(organizationId);
      return sendJson(res, 200, { media: items.map(({ source_url, thumbnail_url, ...item }) => ({ ...item, organization_id: organizationId, thumbnail_url: thumbnail_url ? `/api/v1/media/${encodeURIComponent(item.media_id)}/thumbnail` : null })) });
    }
    if (method === 'POST' && mediaMatch?.[2] === 'view') {
      const session = authorizeSession(storage, req, now()); const mediaId = decodePathPart(mediaMatch[1]); const organizationId = req.headers['x-organization-id'];
      if (typeof organizationId !== 'string' || !organizationId) throw new ApiError(400, 'ORG_CONTEXT_INVALID', 'missing X-Organization-Id header'); requireActiveMembership(storage, session.installation_id, organizationId);
      if (!provider) throw new ApiError(503, 'MEDIA_PROVIDER_UNAVAILABLE', 'Pagaska Drive provider is not configured');
      const items = await provider.listMedia(organizationId); const item = items.find((candidate) => candidate.media_id === mediaId);
      if (!item) throw ApiError.notFound('UNKNOWN_MEDIA', 'media does not exist or is not available to this organization');
      if (typeof item.source_url !== 'string' || !item.source_url) throw new ApiError(502, 'MEDIA_PROVIDER_ERROR', 'media provider returned no source');
      const accessToken = newSessionToken(); const createdAt = now(); const expiresAt = Math.min(createdAt + config.mediaViewTtlMs, session.expires_at);
      storage.createMediaView({ viewTokenHash: hashToken(accessToken), installationId: session.installation_id, organizationId, mediaId, sourceUrl: item.source_url, mimeType: item.mime_type, expiresAt, createdAt });
      return sendJson(res, 200, { media_id: item.media_id, type: item.type, mime_type: item.mime_type, expires_at_epoch_ms: expiresAt, content_url: `/api/v1/media/${encodeURIComponent(item.media_id)}/content`, access_token: accessToken });
    }
    if (method === 'GET' && mediaMatch?.[2] === 'thumbnail') {
      const session = authorizeSession(storage, req, now()); const mediaId = decodePathPart(mediaMatch[1]); const organizationId = req.headers['x-organization-id'];
      if (typeof organizationId !== 'string' || !organizationId) throw new ApiError(400, 'ORG_CONTEXT_INVALID', 'missing X-Organization-Id header'); requireActiveMembership(storage, session.installation_id, organizationId);
      if (!provider) throw new ApiError(503, 'MEDIA_PROVIDER_UNAVAILABLE', 'Pagaska Drive provider is not configured');
      const items = await provider.listMedia(organizationId); const item = items.find((candidate) => candidate.media_id === mediaId);
      if (!item || !item.thumbnail_url) throw ApiError.notFound('THUMBNAIL_UNAVAILABLE', 'thumbnail does not exist or is not available');
      const upstream = await provider.openMedia(item.thumbnail_url);
      const upstreamMime = upstream.headers.get('content-type')?.split(';', 1)[0]?.trim();
      await streamResponse(req, res, upstream, upstreamMime, log);
      return;
    }
    if (method === 'GET' && mediaMatch?.[2] === 'content') {
      const session = authorizeSession(storage, req, now()); const mediaId = decodePathPart(mediaMatch[1]); const organizationId = req.headers['x-organization-id']; const viewToken = req.headers['x-media-view-token'];
      if (typeof organizationId !== 'string' || !organizationId || typeof viewToken !== 'string' || !viewToken) throw new ApiError(400, 'MEDIA_VIEW_INVALID', 'missing media view credentials');
      requireActiveMembership(storage, session.installation_id, organizationId); const view = storage.getMediaView(hashToken(viewToken), now());
      if (!view || view.installation_id !== session.installation_id || view.organization_id !== organizationId || view.media_id !== mediaId) throw ApiError.unauthorized('MEDIA_VIEW_INVALID', 'media view grant is invalid or expired');
      if (!provider) throw new ApiError(503, 'MEDIA_PROVIDER_UNAVAILABLE', 'Pagaska Drive provider is not configured');
      const upstream = await provider.openMedia(view.source_url);
      await streamResponse(req, res, upstream, view.mime_type, log);
      return;
    }
    throw ApiError.notFound('UNKNOWN_ROUTE', 'route not found');
  }
  return server;
}

function decodePathPart(value) { try { return decodeURIComponent(value); } catch { throw ApiError.malformed('media id is not valid URL encoding'); } }
function safePath(rawUrl) { try { return new URL(rawUrl, 'http://localhost').pathname; } catch { return '/'; } }
function handleError(res, err, log) { if (res.headersSent || res.destroyed) { if (!res.destroyed) res.destroy(err); return; } if (err instanceof ApiError) { sendJson(res, err.status, err.toBody()); return; } log(`[api] internal error: ${err && err.message ? err.message : err}`); sendJson(res, 500, { error: { code: 'INTERNAL_ERROR', message: 'internal server error' } }); }
