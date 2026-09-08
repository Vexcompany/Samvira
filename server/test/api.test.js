import test from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import {
  generateKeyPair,
  get,
  post,
  registerAndVerify,
  startServer,
} from './helpers.js';

test('registers a new installation', async () => {
  const ctx = await startServer();
  try {
    const keyPair = generateKeyPair();
    const res = await post(ctx.base, '/api/v1/installations/register', {
      installation_id: 'inst-1',
      public_key_pem: keyPair.publicKey.export({ type: 'spki', format: 'pem' }),
    });
    assert.equal(res.status, 200);
    assert.equal(res.body.installation_id, 'inst-1');
    assert.equal(res.body.already_registered, false);
    assert.equal(typeof res.body.registered_at, 'number');
  } finally {
    await ctx.close();
  }
});

test('registration is idempotent for the same key', async () => {
  const ctx = await startServer();
  try {
    const keyPair = generateKeyPair();
    const pem = keyPair.publicKey.export({ type: 'spki', format: 'pem' });
    const first = await post(ctx.base, '/api/v1/installations/register', {
      installation_id: 'inst-1',
      public_key_pem: pem,
    });
    const second = await post(ctx.base, '/api/v1/installations/register', {
      installation_id: 'inst-1',
      public_key_pem: pem,
    });
    assert.equal(first.status, 200);
    assert.equal(second.status, 200);
    assert.equal(second.body.already_registered, true);
  } finally {
    await ctx.close();
  }
});

test('registration conflicts when the key differs', async () => {
  const ctx = await startServer();
  try {
    const first = generateKeyPair();
    const second = generateKeyPair();
    await post(ctx.base, '/api/v1/installations/register', {
      installation_id: 'inst-1',
      public_key_pem: first.publicKey.export({ type: 'spki', format: 'pem' }),
    });
    const conflict = await post(ctx.base, '/api/v1/installations/register', {
      installation_id: 'inst-1',
      public_key_pem: second.publicKey.export({ type: 'spki', format: 'pem' }),
    });
    assert.equal(conflict.status, 409);
    assert.equal(conflict.body.error.code, 'INSTALLATION_CONFLICT');
  } finally {
    await ctx.close();
  }
});

test('registration rejects a malformed public key', async () => {
  const ctx = await startServer();
  try {
    const res = await post(ctx.base, '/api/v1/installations/register', {
      installation_id: 'inst-1',
      public_key_pem: 'definitely not a key',
    });
    assert.equal(res.status, 400);
    assert.equal(res.body.error.code, 'MALFORMED_REQUEST');
  } finally {
    await ctx.close();
  }
});

test('challenge for an unknown installation is rejected', async () => {
  const ctx = await startServer();
  try {
    const res = await post(ctx.base, '/api/v1/installations/challenge', {
      installation_id: 'ghost',
    });
    assert.equal(res.status, 404);
    assert.equal(res.body.error.code, 'UNKNOWN_INSTALLATION');
  } finally {
    await ctx.close();
  }
});

test('rejects a malformed request body', async () => {
  const ctx = await startServer();
  try {
    const res = await fetch(`${ctx.base}/api/v1/installations/register`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: '{ not json',
    });
    assert.equal(res.status, 400);
    const body = await res.json();
    assert.equal(body.error.code, 'MALFORMED_REQUEST');
  } finally {
    await ctx.close();
  }
});

test('rejects a request missing required fields', async () => {
  const ctx = await startServer();
  try {
    const res = await post(ctx.base, '/api/v1/installations/register', {});
    assert.equal(res.status, 400);
    assert.equal(res.body.error.code, 'MALFORMED_REQUEST');
  } finally {
    await ctx.close();
  }
});

test('tolerates unexpected extra request fields', async () => {
  const ctx = await startServer();
  try {
    const keyPair = generateKeyPair();
    const res = await post(ctx.base, '/api/v1/installations/register', {
      installation_id: 'inst-1',
      public_key_pem: keyPair.publicKey.export({ type: 'spki', format: 'pem' }),
      surprise_field: 'ignored',
    });
    assert.equal(res.status, 200);
  } finally {
    await ctx.close();
  }
});

test('completes the full registration and proof-of-possession flow', async () => {
  const ctx = await startServer();
  try {
    const { verify } = await registerAndVerify(ctx.base, {
      installationId: 'inst-1',
      keyPair: generateKeyPair(),
    });
    assert.equal(verify.status, 200);
    assert.equal(verify.body.installation_id, 'inst-1');
    assert.equal(typeof verify.body.session_token, 'string');
    assert.ok(verify.body.session_token.length > 20);
    assert.equal(typeof verify.body.session_expires_at_epoch_ms, 'number');
  } finally {
    await ctx.close();
  }
});

test('rejects an invalid signature', async () => {
  const ctx = await startServer();
  try {
    const keyPair = generateKeyPair();
    const pem = keyPair.publicKey.export({ type: 'spki', format: 'pem' });
    await post(ctx.base, '/api/v1/installations/register', {
      installation_id: 'inst-1',
      public_key_pem: pem,
    });
    const challenge = await post(ctx.base, '/api/v1/installations/challenge', {
      installation_id: 'inst-1',
    });
    const bogus = crypto.sign('sha256', Buffer.from('wrong data'), generateKeyPair().privateKey);
    const verify = await post(ctx.base, '/api/v1/installations/verify', {
      installation_id: 'inst-1',
      challenge_id: challenge.body.challenge_id,
      signature_b64: bogus.toString('base64url'),
    });
    assert.equal(verify.status, 401);
    assert.equal(verify.body.error.code, 'INVALID_SIGNATURE');
  } finally {
    await ctx.close();
  }
});

test('rejects a challenge used by a different installation', async () => {
  const ctx = await startServer();
  try {
    const keyPair = generateKeyPair();
    const pem = keyPair.publicKey.export({ type: 'spki', format: 'pem' });
    await post(ctx.base, '/api/v1/installations/register', {
      installation_id: 'inst-1',
      public_key_pem: pem,
    });
    const challenge = await post(ctx.base, '/api/v1/installations/challenge', {
      installation_id: 'inst-1',
    });
    const nonce = Buffer.from(challenge.body.nonce_b64, 'base64url');
    const signature = crypto.sign('sha256', nonce, keyPair.privateKey);
    const verify = await post(ctx.base, '/api/v1/installations/verify', {
      installation_id: 'inst-other',
      challenge_id: challenge.body.challenge_id,
      signature_b64: signature.toString('base64url'),
    });
    assert.equal(verify.status, 403);
    assert.equal(verify.body.error.code, 'CHALLENGE_MISMATCH');
  } finally {
    await ctx.close();
  }
});

test('rejects an unknown challenge', async () => {
  const ctx = await startServer();
  try {
    const res = await post(ctx.base, '/api/v1/installations/verify', {
      installation_id: 'inst-1',
      challenge_id: 'does-not-exist',
      signature_b64: 'AAAA',
    });
    assert.equal(res.status, 404);
    assert.equal(res.body.error.code, 'UNKNOWN_CHALLENGE');
  } finally {
    await ctx.close();
  }
});

test('rejects a replayed challenge', async () => {
  const ctx = await startServer();
  try {
    const keyPair = generateKeyPair();
    const first = await registerAndVerify(ctx.base, { installationId: 'inst-1', keyPair });
    assert.equal(first.verify.status, 200);
    // Replay the same challenge with a freshly computed (still valid) signature.
    const signature = crypto.sign('sha256', first.nonce, keyPair.privateKey);
    const replay = await post(ctx.base, '/api/v1/installations/verify', {
      installation_id: 'inst-1',
      challenge_id: first.challenge.body.challenge_id,
      signature_b64: signature.toString('base64url'),
    });
    assert.equal(replay.status, 409);
    assert.equal(replay.body.error.code, 'CHALLENGE_REPLAYED');
  } finally {
    await ctx.close();
  }
});

test('rejects an expired challenge', async () => {
  let nowMs = 1_000_000;
  const ctx = await startServer({ now: () => nowMs });
  try {
    const keyPair = generateKeyPair();
    const pem = keyPair.publicKey.export({ type: 'spki', format: 'pem' });
    await post(ctx.base, '/api/v1/installations/register', {
      installation_id: 'inst-1',
      public_key_pem: pem,
    });
    const challenge = await post(ctx.base, '/api/v1/installations/challenge', {
      installation_id: 'inst-1',
    });
    // Advance past the 60s challenge TTL.
    nowMs += 60_001;
    const nonce = Buffer.from(challenge.body.nonce_b64, 'base64url');
    const signature = crypto.sign('sha256', nonce, keyPair.privateKey);
    const verify = await post(ctx.base, '/api/v1/installations/verify', {
      installation_id: 'inst-1',
      challenge_id: challenge.body.challenge_id,
      signature_b64: signature.toString('base64url'),
    });
    assert.equal(verify.status, 410);
    assert.equal(verify.body.error.code, 'CHALLENGE_EXPIRED');
  } finally {
    await ctx.close();
  }
});

test('rejects a malformed signature encoding', async () => {
  const ctx = await startServer();
  try {
    const keyPair = generateKeyPair();
    const pem = keyPair.publicKey.export({ type: 'spki', format: 'pem' });
    await post(ctx.base, '/api/v1/installations/register', {
      installation_id: 'inst-1',
      public_key_pem: pem,
    });
    const challenge = await post(ctx.base, '/api/v1/installations/challenge', {
      installation_id: 'inst-1',
    });
    const verify = await post(ctx.base, '/api/v1/installations/verify', {
      installation_id: 'inst-1',
      challenge_id: challenge.body.challenge_id,
      signature_b64: '###not-base64url###',
    });
    assert.equal(verify.status, 400);
    assert.equal(verify.body.error.code, 'MALFORMED_REQUEST');
  } finally {
    await ctx.close();
  }
});

test('revokes a session', async () => {
  const ctx = await startServer();
  try {
    const { verify } = await registerAndVerify(ctx.base, {
      installationId: 'inst-1',
      keyPair: generateKeyPair(),
    });
    const token = verify.body.session_token;
    const revoke = await post(ctx.base, '/api/v1/session/revoke', {}, {
      authorization: `Bearer ${token}`,
    });
    assert.equal(revoke.status, 200);
    assert.equal(revoke.body.revoked, true);
  } finally {
    await ctx.close();
  }
});

test('rejects a revoked session on authed endpoints', async () => {
  const ctx = await startServer();
  try {
    const { verify } = await registerAndVerify(ctx.base, {
      installationId: 'inst-1',
      keyPair: generateKeyPair(),
    });
    const token = verify.body.session_token;
    await post(ctx.base, '/api/v1/session/revoke', {}, { authorization: `Bearer ${token}` });
    const again = await post(ctx.base, '/api/v1/session/revoke', {}, {
      authorization: `Bearer ${token}`,
    });
    assert.equal(again.status, 401);
    assert.equal(again.body.error.code, 'SESSION_REVOKED');
  } finally {
    await ctx.close();
  }
});

test('rejects an expired session', async () => {
  let nowMs = 1_000_000;
  const ctx = await startServer({ now: () => nowMs });
  try {
    const { verify } = await registerAndVerify(ctx.base, {
      installationId: 'inst-1',
      keyPair: generateKeyPair(),
    });
    const token = verify.body.session_token;
    // Advance past the 24h session TTL.
    nowMs += 24 * 60 * 60 * 1000 + 1;
    const res = await post(ctx.base, '/api/v1/session/revoke', {}, {
      authorization: `Bearer ${token}`,
    });
    assert.equal(res.status, 401);
    assert.equal(res.body.error.code, 'SESSION_EXPIRED');
  } finally {
    await ctx.close();
  }
});

test('rejects requests without an authorization header', async () => {
  const ctx = await startServer();
  try {
    const res = await post(ctx.base, '/api/v1/session/revoke', {});
    assert.equal(res.status, 401);
    assert.equal(res.body.error.code, 'SESSION_INVALID');
  } finally {
    await ctx.close();
  }
});

test('returns 404 for unknown routes', async () => {
  const ctx = await startServer();
  try {
    const res = await get(ctx.base, '/api/v1/does-not-exist');
    assert.equal(res.status, 404);
    assert.equal(res.body.error.code, 'UNKNOWN_ROUTE');
  } finally {
    await ctx.close();
  }
});
