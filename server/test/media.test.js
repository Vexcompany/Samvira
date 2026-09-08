import test from 'node:test';
import assert from 'node:assert/strict';
import { get, post, registerAndVerify, startServer, generateKeyPair } from './helpers.js';
import { hashToken } from '../src/security.js';

function mediaProvider() {
  const source = 'https://drive.internal/media/photo-1';
  const thumbnail = 'https://drive.internal/thumb/photo-1';
  return {
    async listMedia() {
      return [{ media_id: 'photo-1', type: 'PHOTO', mime_type: 'image/jpeg', width: 1200, height: 800, duration_ms: null, created_at_epoch_ms: 1700000000000, thumbnail_url: thumbnail, source_url: source }];
    },
    async openMedia(url) {
      if (url === thumbnail) return new Response('thumbnail-bytes', { status: 200, headers: { 'content-type': 'image/webp' } });
      assert.equal(url, source);
      return new Response('image-bytes', { status: 200, headers: { 'content-type': 'image/jpeg' } });
    },
  };
}

async function authenticatedMediaServer({ config = {}, now } = {}) {
  const ctx = await startServer({ provider: mediaProvider(), config, now });
  const keyPair = generateKeyPair();
  const auth = await registerAndVerify(ctx.base, { installationId: 'media-inst', keyPair });
  ctx.storage.createOrganization('org-1', 'Test Org', now ? now() : Date.now());
  ctx.storage.addMembership('media-inst', 'org-1', 'ACTIVE', now ? now() : Date.now());
  return { ctx, token: auth.verify.body.session_token };
}

test('media listing is organization-scoped and never exposes provider URLs', async () => {
  const { ctx, token } = await authenticatedMediaServer();
  try {
    const res = await get(ctx.base, '/api/v1/media?organization_id=org-1', { Authorization: `Bearer ${token}`, 'X-Organization-Id': 'org-1' });
    assert.equal(res.status, 200);
    assert.equal(res.body.media[0].media_id, 'photo-1');
    assert.equal(res.body.media[0].thumbnail_url, '/api/v1/media/photo-1/thumbnail');
    assert.equal(JSON.stringify(res.body).includes('drive.internal'), false);
  } finally { await ctx.close(); }
});

test('authorized thumbnail gateway never exposes the provider URL', async () => {
  const { ctx, token } = await authenticatedMediaServer();
  try {
    const denied = await get(ctx.base, '/api/v1/media/photo-1/thumbnail');
    assert.equal(denied.status, 401);
    const allowed = await get(ctx.base, '/api/v1/media/photo-1/thumbnail', { Authorization: `Bearer ${token}`, 'X-Organization-Id': 'org-1' });
    assert.equal(allowed.status, 200);
    assert.equal(allowed.headers.get('content-type'), 'image/webp');
    assert.equal(await allowed.text(), 'thumbnail-bytes');
  } finally { await ctx.close(); }
});

test('media view creates a short-lived installation-bound gateway grant', async () => {
  const { ctx, token } = await authenticatedMediaServer();
  try {
    const res = await post(ctx.base, '/api/v1/media/photo-1/view', {}, { Authorization: `Bearer ${token}`, 'X-Organization-Id': 'org-1' });
    assert.equal(res.status, 200);
    assert.equal(res.body.content_url, '/api/v1/media/photo-1/content');
    assert.equal(typeof res.body.access_token, 'string');
    assert.equal(res.body.expires_at_epoch_ms > Date.now(), true);
    assert.equal(res.body.content_url.includes('drive.internal'), false);
  } finally { await ctx.close(); }
});

test('media view expiry never outlives the authenticated session', async () => {
  const currentTime = 1_700_000_000_000;
  const now = () => currentTime;
  const { ctx, token } = await authenticatedMediaServer({ config: { sessionTtlMs: 1_000, mediaViewTtlMs: 5_000 }, now });
  try {
    const res = await post(ctx.base, '/api/v1/media/photo-1/view', {}, { Authorization: `Bearer ${token}`, 'X-Organization-Id': 'org-1' });
    assert.equal(res.status, 200);
    assert.equal(res.body.expires_at_epoch_ms, currentTime + 1_000);
    const persisted = ctx.storage.getMediaView(hashToken(res.body.access_token), currentTime);
    assert.equal(persisted.expires_at, currentTime + 1_000);
  } finally { await ctx.close(); }
});

test('media content requires the issued view token', async () => {
  const { ctx, token } = await authenticatedMediaServer();
  try {
    const grant = await post(ctx.base, '/api/v1/media/photo-1/view', {}, { Authorization: `Bearer ${token}`, 'X-Organization-Id': 'org-1' });
    const denied = await fetch(`${ctx.base}${grant.body.content_url}`, { headers: { Authorization: `Bearer ${token}`, 'X-Organization-Id': 'org-1' } });
    assert.equal(denied.status, 400);
    const allowed = await fetch(`${ctx.base}${grant.body.content_url}`, { headers: { Authorization: `Bearer ${token}`, 'X-Organization-Id': 'org-1', 'X-Media-View-Token': grant.body.access_token } });
    assert.equal(allowed.status, 200);
    assert.equal(await allowed.text(), 'image-bytes');
  } finally { await ctx.close(); }
});

test('failed upstream media stream is contained after response starts', async () => {
  const source = 'https://drive.internal/media/broken';
  const provider = {
    async listMedia() {
      return [{ media_id: 'broken', type: 'PHOTO', mime_type: 'image/jpeg', width: 10, height: 10, duration_ms: null, created_at_epoch_ms: 1700000000000, thumbnail_url: null, source_url: source }];
    },
    async openMedia(url) {
      assert.equal(url, source);
      const body = new ReadableStream({
        start(controller) {
          controller.enqueue(new TextEncoder().encode('partial'));
          setTimeout(() => controller.error(new Error('upstream boom')), 10);
        },
      });
      return new Response(body, { status: 200, headers: { 'content-type': 'image/jpeg' } });
    },
  };
  const ctx = await startServer({ provider });
  const keyPair = generateKeyPair();
  const auth = await registerAndVerify(ctx.base, { installationId: 'broken-inst', keyPair });
  ctx.storage.createOrganization('org-1', 'Test Org', Date.now());
  ctx.storage.addMembership('broken-inst', 'org-1', 'ACTIVE', Date.now());
  try {
    const grant = await post(ctx.base, '/api/v1/media/broken/view', {}, { Authorization: `Bearer ${auth.verify.body.session_token}`, 'X-Organization-Id': 'org-1' });
    assert.equal(grant.status, 200);
    const response = await fetch(`${ctx.base}${grant.body.content_url}`, { headers: { Authorization: `Bearer ${auth.verify.body.session_token}`, 'X-Organization-Id': 'org-1', 'X-Media-View-Token': grant.body.access_token } });
    assert.equal(response.status, 200);
    await assert.rejects(() => response.arrayBuffer());
    const health = await fetch(`${ctx.base}/healthz`);
    assert.equal(health.status, 200);
  } finally { await ctx.close(); }
});
