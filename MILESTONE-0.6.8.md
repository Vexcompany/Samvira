# SAMVIRA — Milestone 0.6.8

## Production hardening

This milestone hardens the existing 0.6 gallery/viewer without changing the view-only security model.

### Implemented

- Sensitive selected media bytes are cleared when the gallery activity stops or the gallery surface is disposed.
- Active media loading is cancelled when the viewer becomes hidden.
- FLAG_SECURE remains enabled for the gallery and Android 13+ recents screenshots are disabled while the protected surface is active.
- Thumbnail cache remains app-private and now has:
  - 5 MB maximum entry size.
  - 64 MB maximum total size.
  - 24-hour TTL.
  - stale/temporary-file pruning.
  - explicit cache clearing.
- Cached thumbnails are cleared when authentication context ends, expires, or organization context changes.
- Original media content is never written to the thumbnail cache.
- Existing short-lived media view grants and 401/403 refresh behavior remain unchanged.

### Security boundary

FLAG_SECURE protects against OS-level screenshots/screen recording on supported Android surfaces, but it cannot prevent external-camera capture or capture on rooted/modified devices.

Thumbnail caching uses the application's internal cache directory. Android documents this location as app-private and suitable for temporary sensitive cache data, while also noting that the system may remove cache files independently. The application therefore performs its own TTL and lifecycle cleanup as an additional privacy measure.

### Validation

CI is expected to run Android debug build/lint, unit tests, instrumented-test compilation, and backend tests for the pull request.

### Next

Continue 0.6.8 with access edge cases and performance/error resilience only where concrete issues are found. Do not weaken the view-only privacy boundary to improve convenience.
