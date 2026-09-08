/**
 * Entry point: opens storage, optionally seeds demo organizations, and starts
 * the HTTP server. No credentials or secrets are involved.
 */
import { loadConfig } from './config.js';
import { createServer } from './api.js';
import { createStorage } from './storage.js';

const config = loadConfig();
const storage = createStorage(config.dbPath);

if (config.seedDemoOrgs) {
  const now = Date.now();
  storage.createOrganization('demo-org-alpha', 'Alpha Community', now);
  storage.createOrganization('demo-org-beta', 'Beta Community', now);
  console.log('[cli] seeded demo organizations');
}

const server = createServer({ storage, config, log: console.log });

server.listen(config.port, config.host, () => {
  console.log(`[cli] samvira backend listening on http://${config.host}:${config.port}`);
});

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => {
    server.close(() => {
      storage.close();
      process.exit(0);
    });
  });
}
