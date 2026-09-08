import test from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import { loadConfig } from '../src/config.js';
import { createStorage } from '../src/storage.js';
import { post, startServer, generateKeyPair, registerAndVerify } from './helpers.js';

test('rejects private keys during registration', async () => {
  const ctx = await startServer();
  try {
    const keyPair = generateKeyPair();
    const res = await post(ctx.base, '/api/v1/installations/register', {
      installation_id: 'private-key',
      public_key_pem: keyPair.privateKey.export({ type: 'pkcs8', format: 'pem' }),
    });
    assert.equal(res.status, 400);
    assert.equal(res.body.error.code, 'MALFORMED_REQUEST');
  } finally { await ctx.close(); }
});

test('rejects JSON null, arrays, and primitives as request bodies', async () => {
  const ctx = await startServer();
  try {
    for (const body of ['null', '[]', '"text"', '123', 'true']) {
      const res = await fetch(`${ctx.base}/api/v1/installations/register`, {
        method: 'POST', headers: { 'content-type': 'application/json' }, body,
      });
      assert.equal(res.status, 400);
      const json = await res.json();
      assert.equal(json.error.code, 'MALFORMED_REQUEST');
    }
  } finally { await ctx.close(); }
});

test('installation organization cap is owned by storage, not callers', () => {
  const storage = createStorage(':memory:', { maxOrgsPerInstallation: 2, maxInstallations: 10 });
  try {
    const now = 1_000;
    storage.registerInstallation('inst', 'unused', now);
    storage.createOrganization('a', 'A', now);
    storage.createOrganization('b', 'B', now);
    storage.createOrganization('c', 'C', now);
    storage.addMembership('inst', 'a', 'ACTIVE', now);
    storage.addMembership('inst', 'b', 'ACTIVE', now);
    assert.throws(() => storage.addMembership('inst', 'c', 'ACTIVE', now), (err) => err.code === 'ORG_LIMIT_EXCEEDED');
  } finally { storage.close(); }
});

test('failed session creation does not consume the challenge', () => {
  const storage = createStorage(':memory:');
  try {
    const now = 1_000;
    storage.createChallenge({ challengeId: 'c', installationId: 'i', nonceB64: 'AA', nonce: Buffer.from([0]), expiresAt: 2_000, nowMs: now });
    assert.throws(() => storage.consumeChallengeAndCreateSession({ challengeId: 'c', nowMs: now, tokenHash: null, installationId: 'i', createdAt: now, expiresAt: 2_000 }));
    assert.equal(storage.getChallenge('c').used_at, null);
  } finally { storage.close(); }
});

test('valid proof still works after hardening', async () => {
  const ctx = await startServer();
  try {
    const result = await registerAndVerify(ctx.base, { installationId: 'inst-proof', keyPair: generateKeyPair() });
    assert.equal(result.verify.status, 200);
  } finally { await ctx.close(); }
});

test('strict configuration rejects malformed explicit values', () => {
  assert.throws(() => loadConfig({ SAMVIRA_PORT: '8787abc' }), /SAMVIRA_PORT/);
  assert.throws(() => loadConfig({ SAMVIRA_PORT: '0' }), /SAMVIRA_PORT/);
  assert.throws(() => loadConfig({ SAMVIRA_SESSION_TTL_MS: '-1' }), /SAMVIRA_SESSION_TTL_MS/);
  assert.throws(() => loadConfig({ SAMVIRA_MAX_ORGS: '0' }), /SAMVIRA_MAX_ORGS/);
  assert.throws(() => loadConfig({ SAMVIRA_BODY_LIMIT_BYTES: '999999999999999999999' }), /SAMVIRA_BODY_LIMIT_BYTES/);
});

test('registration rate limit returns 429', async () => {
  const ctx = await startServer({ config: { registrationRequestsPerWindow: 1, rateLimitWindowMs: 60_000 } });
  try {
    const keyPair = generateKeyPair();
    const pem = keyPair.publicKey.export({ type: 'spki', format: 'pem' });
    const first = await post(ctx.base, '/api/v1/installations/register', { installation_id: 'one', public_key_pem: pem });
    const second = await post(ctx.base, '/api/v1/installations/register', { installation_id: 'two', public_key_pem: pem });
    assert.equal(first.status, 200);
    assert.equal(second.status, 429);
  } finally { await ctx.close(); }
});
