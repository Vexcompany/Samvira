# SAMVIRA 0.6.7 — Auth & Data Lifecycle

Milestone 0.6.7 hardens the app around the lifetime of its backend session.

## Included

- Existing persisted sessions are restored on startup only when they are still valid.
- Active sessions are monitored while the Home screen is alive.
- Expired sessions are automatically revoked/cleared locally and the organization context is cleared.
- Session rejection from organization loading or context selection fails closed and returns the app to the disconnected state.
- Organization loading errors remain visible instead of silently looking like an empty organization list.
- Session generation guards prevent older asynchronous requests from repopulating state after sign-out or session replacement.
- Manual sign-out clears the selected organization and all organization-related transient state.

## Validation

The changes were reviewed against the current authentication, DataStore session, organization, and navigation architecture.

GitHub Actions are currently unavailable for this repository/account, so this milestone does not claim CI/build verification. The local environment used for repository inspection also cannot resolve github.com, so no local Gradle build result is claimed.

## Next

0.6.8 will focus on production hardening: media/cache lifecycle, background/lifecycle behavior, access edge cases, and performance/error resilience.
