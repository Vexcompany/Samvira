import test from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import {
  fromB64url,
  hashToken,
  newNonce,
  newSessionToken,
  toB64url,
  verifySignature,
} from '../src/security.js';

test('base64url round-trips arbitrary bytes', () => {
  const bytes = crypto.randomBytes(64);
  assert.deepEqual(fromB64url(toB64url(bytes)), bytes);
});

test('nonce is 32 random bytes and does not repeat', () => {
  const a = newNonce();
  const b = newNonce();
  assert.equal(a.length, 32);
  assert.equal(b.length, 32);
  assert.notDeepEqual(a, b);
});

test('session tokens are distinct and hash deterministically', () => {
  const a = newSessionToken();
  const b = newSessionToken();
  assert.notEqual(a, b);
  assert.equal(hashToken(a), hashToken(a));
  assert.notEqual(hashToken(a), hashToken(b));
});

test('verifySignature accepts a valid DER signature over the data', () => {
  const { publicKey, privateKey } = generateKeyPair();
  const pem = publicKey.export({ type: 'spki', format: 'pem' });
  const data = crypto.randomBytes(32);
  const signature = crypto.sign('sha256', data, privateKey);
  assert.equal(verifySignature({ publicKeyPem: pem, data, signature }), true);
});

test('verifySignature rejects tampered data', () => {
  const { publicKey, privateKey } = generateKeyPair();
  const pem = publicKey.export({ type: 'spki', format: 'pem' });
  const data = crypto.randomBytes(32);
  const signature = crypto.sign('sha256', data, privateKey);
  assert.equal(
    verifySignature({ publicKeyPem: pem, data: crypto.randomBytes(32), signature }),
    false,
  );
});

test('verifySignature rejects a signature from a different key', () => {
  const first = generateKeyPair();
  const second = generateKeyPair();
  const pem = first.publicKey.export({ type: 'spki', format: 'pem' });
  const data = crypto.randomBytes(32);
  const signature = crypto.sign('sha256', data, second.privateKey);
  assert.equal(verifySignature({ publicKeyPem: pem, data, signature }), false);
});

test('verifySignature returns false for a malformed public key', () => {
  assert.equal(
    verifySignature({
      publicKeyPem: 'not a pem',
      data: Buffer.from('data'),
      signature: Buffer.from('sig'),
    }),
    false,
  );
});

function generateKeyPair() {
  return crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
}
