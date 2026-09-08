/**
 * Environment-driven configuration with conservative defaults.
 *
 * Every value has a safe default and can be overridden via environment
 * variables. There are no secrets in this file: connection details (port,
 * database path) are operational configuration, never credentials.
 */

function toInt(value, fallback) {
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

export function loadConfig(env = process.env) {
  return {
    host: env.SAMVIRA_HOST || '0.0.0.0',
    port: toInt(env.SAMVIRA_PORT, 8787),
    dbPath: env.SAMVIRA_DB || 'samvira.db',
    // How long a proof-of-possession challenge stays valid (ms).
    challengeTtlMs: toInt(env.SAMVIRA_CHALLENGE_TTL_MS, 60_000),
    // How long a bootstrap session token stays valid (ms).
    sessionTtlMs: toInt(env.SAMVIRA_SESSION_TTL_MS, 24 * 60 * 60 * 1000),
    // Server-side cap on organizations per installation.
    maxOrgsPerInstallation: toInt(env.SAMVIRA_MAX_ORGS, 2),
    // Maximum accepted request body size in bytes.
    bodyLimitBytes: toInt(env.SAMVIRA_BODY_LIMIT_BYTES, 256 * 1024),
    // Dev-only convenience: create a couple of demo organizations at startup.
    seedDemoOrgs: env.SAMVIRA_SEED_DEMO === '1',
  };
}
