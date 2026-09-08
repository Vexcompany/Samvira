/** Environment-driven configuration with strict validation. */
function positiveInt(value, fallback, name, { min = 1, max = Number.MAX_SAFE_INTEGER } = {}) {
  if (value == null || value === '') return fallback;
  if (!/^[0-9]+$/.test(value)) throw new Error(`${name} must be an integer`);
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed < min || parsed > max) throw new Error(`${name} must be between ${min} and ${max}`);
  return parsed;
}

export function loadConfig(env = process.env) {
  return {
    host: env.SAMVIRA_HOST || '0.0.0.0',
    port: positiveInt(env.SAMVIRA_PORT, 8787, 'SAMVIRA_PORT', { max: 65_535 }),
    dbPath: env.SAMVIRA_DB || 'samvira.db',
    challengeTtlMs: positiveInt(env.SAMVIRA_CHALLENGE_TTL_MS, 60_000, 'SAMVIRA_CHALLENGE_TTL_MS'),
    sessionTtlMs: positiveInt(env.SAMVIRA_SESSION_TTL_MS, 24 * 60 * 60 * 1000, 'SAMVIRA_SESSION_TTL_MS'),
    mediaViewTtlMs: positiveInt(env.SAMVIRA_MEDIA_VIEW_TTL_MS, 5 * 60 * 1000, 'SAMVIRA_MEDIA_VIEW_TTL_MS'),
    maxOrgsPerInstallation: positiveInt(env.SAMVIRA_MAX_ORGS, 2, 'SAMVIRA_MAX_ORGS', { max: 100 }),
    bodyLimitBytes: positiveInt(env.SAMVIRA_BODY_LIMIT_BYTES, 256 * 1024, 'SAMVIRA_BODY_LIMIT_BYTES', { max: 10 * 1024 * 1024 }),
    maxInstallations: positiveInt(env.SAMVIRA_MAX_INSTALLATIONS, 10_000, 'SAMVIRA_MAX_INSTALLATIONS'),
    rateLimitWindowMs: positiveInt(env.SAMVIRA_RATE_LIMIT_WINDOW_MS, 60_000, 'SAMVIRA_RATE_LIMIT_WINDOW_MS'),
    registrationRequestsPerWindow: positiveInt(env.SAMVIRA_REGISTRATION_RATE_LIMIT, 30, 'SAMVIRA_REGISTRATION_RATE_LIMIT'),
    challengeRequestsPerWindow: positiveInt(env.SAMVIRA_CHALLENGE_RATE_LIMIT, 60, 'SAMVIRA_CHALLENGE_RATE_LIMIT'),
    pagaskaDriveBaseUrl: env.SAMVIRA_PAGASKA_DRIVE_URL || '',
    seedDemoOrgs: env.SAMVIRA_SEED_DEMO === '1',
  };
}
