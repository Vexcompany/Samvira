/** Shared test helpers. */
import crypto from 'node:crypto';
import { createServer } from '../src/api.js';
import { createStorage } from '../src/storage.js';

export const DEFAULT_CONFIG = {
  challengeTtlMs: 60_000,
  sessionTtlMs: 24 * 60 * 60 * 1000,
  maxOrgsPerInstallation: 2,
  maxInstallations: 10_000,
  bodyLimitBytes: 256 * 1024,
  rateLimitWindowMs: 60_000,
  registrationRequestsPerWindow: 30,
  challengeRequestsPerWindow: 60,
};

export async function startServer({ config = {}, now = () => Date.now(), log = () => {} } = {}) {
  const mergedConfig = { ...DEFAULT_CONFIG, ...config };
  const storage = createStorage(':memory:', {
    maxOrgsPerInstallation: mergedConfig.maxOrgsPerInstallation,
    maxInstallations: mergedConfig.maxInstallations,
  });
  const server = createServer({ storage, config: mergedConfig, now, log });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address();
  return { server, storage, base: `http://127.0.0.1:${port}`, close: () => new Promise((resolve) => server.close(resolve)) };
}

export function generateKeyPair() { return crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' }); }

export async function post(base, path, body, headers = {}) {
  const res = await fetch(`${base}${path}`, { method: 'POST', headers: { 'content-type': 'application/json', ...headers }, body: JSON.stringify(body) });
  return { status: res.status, body: await res.json() };
}

export async function get(base, path, headers = {}) {
  const res = await fetch(`${base}${path}`, { headers });
  return { status: res.status, body: await res.json() };
}

export async function registerAndVerify(base, { installationId, keyPair }) {
  const publicKeyPem = keyPair.publicKey.export({ type: 'spki', format: 'pem' });
  const register = await post(base, '/api/v1/installations/register', { installation_id: installationId, public_key_pem: publicKeyPem });
  const challenge = await post(base, '/api/v1/installations/challenge', { installation_id: installationId });
  const nonce = Buffer.from(challenge.body.nonce_b64, 'base64url');
  const signature = crypto.sign('sha256', nonce, keyPair.privateKey);
  const verify = await post(base, '/api/v1/installations/verify', { installation_id: installationId, challenge_id: challenge.body.challenge_id, signature_b64: signature.toString('base64url') });
  return { register, challenge, verify, nonce };
}
