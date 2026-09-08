import test from 'node:test';
import assert from 'node:assert/strict';
import { createStorage } from '../src/storage.js';
import {
  generateKeyPair,
  get,
  post,
  registerAndVerify,
  startServer,
} from './helpers.js';

const MAX_ORGS = 2;

async function authenticatedInstallation(ctx, installationId) {
  const keyPair = generateKeyPair();
  const { verify } = await registerAndVerify(ctx.base, { installationId, keyPair });
  const token = verify.body.session_token;
  return { token, auth: { authorization: `Bearer ${token}` } };
}

test('lists zero organizations for an installation with no memberships', async () => {
  const ctx = await startServer();
  try {
    const { auth } = await authenticatedInstallation(ctx, 'inst-1');
    const res = await get(ctx.base, '/api/v1/organizations', auth);
    assert.equal(res.status, 200);
    assert.deepEqual(res.body.organizations, []);
  } finally {
    await ctx.close();
  }
});

test('lists one organization with its membership state', async () => {
  const ctx = await startServer();
  try {
    ctx.storage.createOrganization('org-a', 'Alpha Community', 1);
    ctx.storage.addMembership('inst-1', 'org-a', 'ACTIVE', 2, MAX_ORGS);
    const { auth } = await authenticatedInstallation(ctx, 'inst-1');
    const res = await get(ctx.base, '/api/v1/organizations', auth);
    assert.equal(res.status, 200);
    assert.equal(res.body.organizations.length, 1);
    assert.deepEqual(res.body.organizations[0], {
      organization_id: 'org-a',
      name: 'Alpha Community',
      state: 'ACTIVE',
    });
  } finally {
    await ctx.close();
  }
});

test('lists two organizations', async () => {
  const ctx = await startServer();
  try {
    ctx.storage.createOrganization('org-a', 'Alpha Community', 1);
    ctx.storage.createOrganization('org-b', 'Beta Community', 2);
    ctx.storage.addMembership('inst-1', 'org-a', 'ACTIVE', 3, MAX_ORGS);
    ctx.storage.addMembership('inst-1', 'org-b', 'PENDING', 4, MAX_ORGS);
    const { auth } = await authenticatedInstallation(ctx, 'inst-1');
    const res = await get(ctx.base, '/api/v1/organizations', auth);
    assert.equal(res.status, 200);
    assert.equal(res.body.organizations.length, 2);
    const states = res.body.organizations.map((o) => o.state).sort();
    assert.deepEqual(states, ['ACTIVE', 'PENDING']);
  } finally {
    await ctx.close();
  }
});

test('enforces the maximum of two organizations per installation server-side', () => {
  const storage = createStorage(':memory:');
  try {
    storage.createOrganization('org-a', 'Alpha Community', 1);
    storage.createOrganization('org-b', 'Beta Community', 2);
    storage.createOrganization('org-c', 'Gamma Community', 3);
    storage.addMembership('inst-1', 'org-a', 'ACTIVE', 4, MAX_ORGS);
    storage.addMembership('inst-1', 'org-b', 'ACTIVE', 5, MAX_ORGS);
    assert.throws(
      () => storage.addMembership('inst-1', 'org-c', 'ACTIVE', 6, MAX_ORGS),
      (err) => err.code === 'ORG_LIMIT_EXCEEDED',
    );
    assert.equal(storage.countMemberships('inst-1'), 2);
  } finally {
    storage.close();
  }
});

test('organization context authorizes an active member', async () => {
  const ctx = await startServer();
  try {
    ctx.storage.createOrganization('org-a', 'Alpha Community', 1);
    ctx.storage.addMembership('inst-1', 'org-a', 'ACTIVE', 2, MAX_ORGS);
    const { auth } = await authenticatedInstallation(ctx, 'inst-1');
    const res = await get(ctx.base, '/api/v1/organizations/org-a/context', {
      ...auth,
      'x-organization-id': 'org-a',
    });
    assert.equal(res.status, 200);
    assert.equal(res.body.organization_id, 'org-a');
    assert.equal(res.body.state, 'ACTIVE');
  } finally {
    await ctx.close();
  }
});

test('organization context rejects an installation that is not a member (cross-organization isolation)', async () => {
  const ctx = await startServer();
  try {
    ctx.storage.createOrganization('org-a', 'Alpha Community', 1);
    ctx.storage.addMembership('inst-1', 'org-a', 'ACTIVE', 2, MAX_ORGS);
    const { auth } = await authenticatedInstallation(ctx, 'inst-2');
    const res = await get(ctx.base, '/api/v1/organizations/org-a/context', {
      ...auth,
      'x-organization-id': 'org-a',
    });
    assert.equal(res.status, 403);
    assert.equal(res.body.error.code, 'NOT_A_MEMBER');
  } finally {
    await ctx.close();
  }
});

test('organization context rejects a revoked membership', async () => {
  const ctx = await startServer();
  try {
    ctx.storage.createOrganization('org-a', 'Alpha Community', 1);
    ctx.storage.addMembership('inst-1', 'org-a', 'REVOKED', 2, MAX_ORGS);
    const { auth } = await authenticatedInstallation(ctx, 'inst-1');
    const res = await get(ctx.base, '/api/v1/organizations/org-a/context', {
      ...auth,
      'x-organization-id': 'org-a',
    });
    assert.equal(res.status, 403);
    assert.equal(res.body.error.code, 'MEMBERSHIP_NOT_ACTIVE');
  } finally {
    await ctx.close();
  }
});

test('organization context rejects a suspended membership', async () => {
  const ctx = await startServer();
  try {
    ctx.storage.createOrganization('org-a', 'Alpha Community', 1);
    ctx.storage.addMembership('inst-1', 'org-a', 'SUSPENDED', 2, MAX_ORGS);
    const { auth } = await authenticatedInstallation(ctx, 'inst-1');
    const res = await get(ctx.base, '/api/v1/organizations/org-a/context', {
      ...auth,
      'x-organization-id': 'org-a',
    });
    assert.equal(res.status, 403);
    assert.equal(res.body.error.code, 'MEMBERSHIP_NOT_ACTIVE');
  } finally {
    await ctx.close();
  }
});

test('organization context rejects a pending membership', async () => {
  const ctx = await startServer();
  try {
    ctx.storage.createOrganization('org-a', 'Alpha Community', 1);
    ctx.storage.addMembership('inst-1', 'org-a', 'PENDING', 2, MAX_ORGS);
    const { auth } = await authenticatedInstallation(ctx, 'inst-1');
    const res = await get(ctx.base, '/api/v1/organizations/org-a/context', {
      ...auth,
      'x-organization-id': 'org-a',
    });
    assert.equal(res.status, 403);
    assert.equal(res.body.error.code, 'MEMBERSHIP_NOT_ACTIVE');
  } finally {
    await ctx.close();
  }
});

test('organization context rejects an unknown organization', async () => {
  const ctx = await startServer();
  try {
    const { auth } = await authenticatedInstallation(ctx, 'inst-1');
    const res = await get(ctx.base, '/api/v1/organizations/does-not-exist/context', {
      ...auth,
      'x-organization-id': 'does-not-exist',
    });
    assert.equal(res.status, 404);
    assert.equal(res.body.error.code, 'UNKNOWN_ORGANIZATION');
  } finally {
    await ctx.close();
  }
});

test('organization context rejects a missing organization header', async () => {
  const ctx = await startServer();
  try {
    ctx.storage.createOrganization('org-a', 'Alpha Community', 1);
    ctx.storage.addMembership('inst-1', 'org-a', 'ACTIVE', 2, MAX_ORGS);
    const { auth } = await authenticatedInstallation(ctx, 'inst-1');
    const res = await get(ctx.base, '/api/v1/organizations/org-a/context', auth);
    assert.equal(res.status, 400);
    assert.equal(res.body.error.code, 'ORG_CONTEXT_INVALID');
  } finally {
    await ctx.close();
  }
});

test('organization context rejects a mismatched organization header', async () => {
  const ctx = await startServer();
  try {
    ctx.storage.createOrganization('org-a', 'Alpha Community', 1);
    ctx.storage.addMembership('inst-1', 'org-a', 'ACTIVE', 2, MAX_ORGS);
    const { auth } = await authenticatedInstallation(ctx, 'inst-1');
    const res = await get(ctx.base, '/api/v1/organizations/org-a/context', {
      ...auth,
      'x-organization-id': 'org-b',
    });
    assert.equal(res.status, 400);
    assert.equal(res.body.error.code, 'ORG_CONTEXT_INVALID');
  } finally {
    await ctx.close();
  }
});

test('membership changes are reflected in the organization list', async () => {
  const ctx = await startServer();
  try {
    ctx.storage.createOrganization('org-a', 'Alpha Community', 1);
    ctx.storage.addMembership('inst-1', 'org-a', 'ACTIVE', 2, MAX_ORGS);
    const { auth } = await authenticatedInstallation(ctx, 'inst-1');
    const before = await get(ctx.base, '/api/v1/organizations', auth);
    assert.equal(before.body.organizations[0].state, 'ACTIVE');

    ctx.storage.setMembershipState('inst-1', 'org-a', 'REVOKED', 3);

    const after = await get(ctx.base, '/api/v1/organizations', auth);
    assert.equal(after.body.organizations[0].state, 'REVOKED');
  } finally {
    await ctx.close();
  }
});

test('organization endpoints reject an invalid session', async () => {
  const ctx = await startServer();
  try {
    const res = await get(ctx.base, '/api/v1/organizations', {
      authorization: 'Bearer not-a-real-token',
    });
    assert.equal(res.status, 401);
    assert.equal(res.body.error.code, 'SESSION_INVALID');
  } finally {
    await ctx.close();
  }
});
