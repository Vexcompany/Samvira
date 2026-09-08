import { ApiError } from './errors.js';

/**
 * Provider-neutral Pagaska Drive adapter.
 *
 * SAMVIRA never forwards provider URLs to clients. The provider API is an
 * internal server-to-server contract and may be replaced without changing
 * the Android media contract.
 *
 * Expected provider responses:
 * GET {base}/v1/organizations/{orgId}/media -> { media: [{ media_id, type,
 * mime_type, width, height, duration_ms, created_at_epoch_ms, thumbnail_url,
 * source_url }] }
 * GET {base}/v1/media/{mediaId}/content -> the media bytes.
 */
export class PagaskaDriveProvider {
  constructor({ baseUrl, fetchImpl = fetch }) {
    this.baseUrl = baseUrl?.trim().replace(/\/$/, '') || '';
    this.fetchImpl = fetchImpl;
  }

  async listMedia(organizationId) {
    this.requireConfigured();
    const response = await this.fetchImpl(`${this.baseUrl}/v1/organizations/${encodeURIComponent(organizationId)}/media`, {
      method: 'GET',
      headers: { accept: 'application/json' },
    });
    if (!response.ok) throw new ApiError(502, 'MEDIA_PROVIDER_ERROR', 'media provider did not return media metadata');
    let body;
    try { body = await response.json(); } catch { throw new ApiError(502, 'MEDIA_PROVIDER_ERROR', 'media provider returned malformed metadata'); }
    if (!body || !Array.isArray(body.media)) throw new ApiError(502, 'MEDIA_PROVIDER_ERROR', 'media provider returned an invalid media list');
    return body.media.map(normalizeMedia);
  }

  async openMedia(sourceUrl) {
    this.requireConfigured();
    if (typeof sourceUrl !== 'string' || sourceUrl.trim() === '') throw new ApiError(502, 'MEDIA_PROVIDER_ERROR', 'media provider returned no source');
    const response = await this.fetchImpl(sourceUrl, { method: 'GET' });
    if (!response.ok || !response.body) throw new ApiError(502, 'MEDIA_PROVIDER_ERROR', 'media provider could not open media');
    return response;
  }

  requireConfigured() {
    if (!this.baseUrl) throw new ApiError(503, 'MEDIA_PROVIDER_UNAVAILABLE', 'Pagaska Drive provider is not configured');
  }
}

function normalizeMedia(item) {
  if (!item || typeof item !== 'object') throw new ApiError(502, 'MEDIA_PROVIDER_ERROR', 'invalid media item');
  const type = item.type === 'PHOTO' || item.type === 'VIDEO' ? item.type : null;
  if (!type || typeof item.media_id !== 'string' || typeof item.mime_type !== 'string' || !Number.isSafeInteger(item.created_at_epoch_ms)) {
    throw new ApiError(502, 'MEDIA_PROVIDER_ERROR', 'invalid media metadata');
  }
  return {
    media_id: item.media_id,
    type,
    mime_type: item.mime_type,
    width: item.width == null ? null : Number.isSafeInteger(item.width) ? item.width : null,
    height: item.height == null ? null : Number.isSafeInteger(item.height) ? item.height : null,
    duration_ms: item.duration_ms == null ? null : Number.isSafeInteger(item.duration_ms) ? item.duration_ms : null,
    created_at_epoch_ms: item.created_at_epoch_ms,
    thumbnail_url: typeof item.thumbnail_url === 'string' ? item.thumbnail_url : null,
    source_url: item.source_url,
  };
}
