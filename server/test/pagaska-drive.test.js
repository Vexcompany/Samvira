import test from 'node:test';
import assert from 'node:assert/strict';
import { PagaskaDriveProvider } from '../src/pagaska-drive.js';

test('Pagaska Drive adapter normalizes provider metadata', async () => {
  const provider = new PagaskaDriveProvider({
    baseUrl: 'https://drive.internal',
    fetchImpl: async (url, options) => {
      assert.equal(url, 'https://drive.internal/v1/organizations/org%2F1/media');
      assert.equal(options.redirect, 'error');
      return new Response(JSON.stringify({ media: [{ media_id: 'm1', type: 'PHOTO', mime_type: 'image/jpeg', created_at_epoch_ms: 1, source_url: 'https://drive.internal/media/m1' }] }), { status: 200 });
    },
  });
  const result = await provider.listMedia('org/1');
  assert.equal(result[0].media_id, 'm1');
  assert.equal(result[0].thumbnail_url, null);
});

test('Pagaska Drive adapter rejects source URLs outside configured origin', async () => {
  const provider = new PagaskaDriveProvider({ baseUrl: 'https://drive.internal', fetchImpl: async () => new Response('ok') });
  await assert.rejects(() => provider.openMedia('https://attacker.example/secret'), (error) => error.code === 'MEDIA_PROVIDER_ERROR');
});

test('Pagaska Drive adapter disables redirects when opening media', async () => {
  const provider = new PagaskaDriveProvider({
    baseUrl: 'https://drive.internal',
    fetchImpl: async (url, options) => {
      assert.equal(url, 'https://drive.internal/media/m1');
      assert.equal(options.redirect, 'error');
      return new Response('image-bytes', { status: 200 });
    },
  });
  const response = await provider.openMedia('https://drive.internal/media/m1');
  assert.equal(await response.text(), 'image-bytes');
});

test('Pagaska Drive adapter rejects unsupported or header-breaking MIME types', async () => {
  for (const mimeType of ['', 'text/plain', 'image/jpeg\r\nX-Injected: yes', 'video/mp4; charset=utf-8']) {
    const provider = new PagaskaDriveProvider({
      baseUrl: 'https://drive.internal',
      fetchImpl: async () => new Response(JSON.stringify({ media: [{ media_id: 'm1', type: 'PHOTO', mime_type: mimeType, created_at_epoch_ms: 1, source_url: 'https://drive.internal/media/m1' }] }), { status: 200 }),
    });
    await assert.rejects(() => provider.listMedia('org-1'), (error) => error.code === 'MEDIA_PROVIDER_ERROR');
  }
});
