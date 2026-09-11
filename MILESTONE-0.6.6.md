# Samvira 0.6.6 — Media playback

Milestone 0.6.6 adds functional video playback to the gallery detail viewer.

## Included

- Media3 ExoPlayer and PlayerView dependencies.
- Video detail requests the same short-lived media-view grant used by the backend content endpoint.
- Video content is fetched through the authenticated `MediaRepository` path before playback, so the raw provider URL never reaches the UI.
- Gallery detail renders an interactive ExoPlayer controller for video content.
- Player resources are released with the Compose lifecycle.
- Existing photo preview behavior remains intact.
- Existing media-access and content-fetch errors remain visible to the user.

## Constraint

The current repository abstraction returns media content as `ByteArray`, so 0.6.6 intentionally plays the authorized video bytes after they are fetched. True streaming/range-based playback can be introduced later without exposing provider URLs, but is outside this milestone.

## Validation note

GitHub Actions is currently unavailable for this repository/account, so CI cannot be used as an automated build/debug gate. Changes are therefore kept deliberately scoped to the existing architecture and API contract and should receive a local Gradle build/install validation when a build environment is available.
