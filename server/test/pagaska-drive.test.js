import test from 'node:test';
import assert from 'node:assert/strict';
import { PagaskaDriveProvider } from '../src/pagaska-drive.js';

test('Pagaska Drive adapter normalizes provider metadata', async () => {
  const provider = new PagaskaDriveProvider({
    baseUrl: 'https://drive.internal',
    fetchImpl: async (url) => {
      assert.equal(url, 'https://drive.internal/v1/organizations/org%2F1/media');
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
