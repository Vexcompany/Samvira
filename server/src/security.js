/**
 * Cryptography helpers for the identity / proof-of-possession flow.
 *
 * Uses only the Node built-in `crypto` module (OpenSSL). The server never sees
 * a client private key: it generates challenges, verifies ECDSA signatures
 * against a registered public key, and issues hashed session tokens.
 */
import crypto from 'node:crypto';

export const NONCE_BYTES = 32;

export function randomBytes(n) {
  return crypto.randomBytes(n);
}

export function newNonce() {
  return crypto.randomBytes(NONCE_BYTES);
}

export function newChallengeId() {
  return crypto.randomBytes(16).toString('hex');
}

export function newSessionToken() {
  return crypto.randomBytes(32).toString('base64url');
}

/**
 * Session tokens are stored hashed (SHA-256) so a database leak does not
 * expose usable bearer tokens.
 */
export function hashToken(token) {
  return crypto.createHash('sha256').update(token, 'utf8').digest('hex');
}

export function toB64url(buffer) {
  return Buffer.from(buffer).toString('base64url');
}

export function fromB64url(value) {
  if (typeof value !== 'string' || value.length === 0) {
    throw new TypeError('value is not a non-empty string');
  }
  // Strict base64url: reject characters outside the alphabet and invalid
  // lengths so malformed encodings fail loudly instead of decoding to garbage.
  if (!/^[A-Za-z0-9_-]*={0,2}$/.test(value)) {
    throw new TypeError('value is not valid base64url');
  }
  if (value.length % 4 === 1) {
    throw new TypeError('value has an invalid base64url length');
  }
  return Buffer.from(value, 'base64url');
}

/**
 * Verifies an ECDSA/SHA-256 signature (DER-encoded, matching the Android
 * `SHA256withECDSA` output) over `data` against a registered public key.
 *
 * Returns `false` for any invalid key, data, or signature instead of throwing,
 * so callers fail closed on a single boolean.
 */
export function verifySignature({ publicKeyPem, data, signature }) {
  try {
    const key = crypto.createPublicKey(publicKeyPem);
    return crypto.verify('sha256', data, key, signature);
  } catch {
    return false;
  }
}
