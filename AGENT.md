**Project name:** SAMVIRA  
**Repository:** Vexcompany/Samvira  
Do not implement this early, but preserve architecture for it.
Use a modern Android stack centered around:
- Clear separation of UI, domain/business logic, data/network, and platform/security concerns.
The backend should remain lightweight. A future server may use Go + SQLite + WebSocket or another similarly lightweight design.
Development is milestone/version based. Each milestone must leave the repository buildable and auditable.

The current baseline is **Milestone 0.2 — Identity & Application Shell Hardening**. Do not regress to 0.1. The next work begins at 0.3.
Historical foundation milestone. Its purpose was to establish the Android project, package structure, Compose shell, navigation, persistence/network abstractions, Keystore identity foundation, logging/error boundaries, tests, CI, README, and a successful build.

### Milestone 0.2 — Identity & Application Shell Hardening

Current completed baseline. Preserve and build on its hardened installation identity, Keystore boundaries, DataStore persistence, networking abstraction, logging/redaction boundary, failure handling, tests, and CI.

Do not undo security hardening or replace working abstractions without a concrete reason.

### Milestone 0.3 — Backend Connectivity & Server Contract

Goal: establish the secure bridge between the Android foundation and a real backend without building the gallery yet.

Implement:

- A versioned, documented client/server API contract.
- Typed request/response models and consistent error handling.
- Backend connectivity behind the existing network abstraction.
- Installation registration using random installation ID + Keystore public key.
- Proof-of-possession authentication using a server challenge and Keystore signature.
- Secure bootstrap/session state with appropriate expiry and revocation handling.
- A minimal backend skeleton with persistent installation records.
- Server-side validation and fail-closed authorization boundaries.
- Tests for malformed responses, unknown installations, invalid signatures, expired/replayed challenges, revoked/expired sessions, and unavailable backend conditions.

Keep the backend implementation replaceable. Do not expose storage-provider URLs or introduce account/password authentication merely for convenience.

**Out of scope for 0.3:** Google Drive/Pagaska Drive media integration, organization UI, albums, gallery/timeline, comments, reactions, download workflow, dashboard, WhatsApp, analytics, and advertising.

### Milestone 0.4 — Organization & Membership Foundation

Goal: make the authenticated installation usable within an organization while keeping authorization server-controlled.

Implement:

- Organization and membership domain models/contracts.
- Server-side membership lookup and authorization decisions.
- Device/installation membership bootstrap after authenticated identity.
- The server-side maximum of 2 organizations per installation.
- Organization selection/switching where the installation belongs to multiple organizations.
- Minimal organization-aware application state and navigation.
- Membership states such as active, pending, revoked, or suspended as appropriate.
- Secure handling of organization-scoped session/context data.
- Tests for unauthorized organization access, revoked membership, invalid organization context, cross-organization data isolation, and membership changes.

The organization layer must prepare the app for media access without implementing the media gallery itself.

**Out of scope for 0.4:** actual Google Drive/Pagaska Drive media retrieval, full album UI, timeline/gallery UI, download approval, comments/reactions, admin dashboard, and messaging integrations.

### Milestone 0.5 — Media Provider / Pagaska Drive Integration

After 0.4, establish the media-provider bridge. Pagaska Drive is the planned authoritative/original media provider.

The provider must remain behind an abstraction so SAMVIRA does not become tightly coupled to storage internals. Raw provider URLs must not leak to unauthorized clients.

### Milestone 0.6+ — Gallery / Timeline / Album Experience

Build the Google Photos-like viewer experience on top of the secured identity, organization, authorization, and media-provider layers.

Expected areas include photo/video grid, timeline, albums, search/discovery, media detail viewing, caching/offline behavior where appropriate, privacy controls, and later controlled download requests.

## 9. Agent execution policy

For the next development cycle, the agent should be allowed to work through **Milestones 0.3 and 0.4**, rather than stopping after only one milestone, **if and only if the repository remains coherent and both milestones can be completed safely**.

Preferred execution order:

1. Inspect the current repository and verify the actual completed state of 0.2.
2. Implement and validate 0.3 completely.
3. If 0.3 passes its quality gate and the architecture is ready, continue directly into 0.4.
4. Validate 0.4 independently before declaring the combined work complete.
5. If 0.4 cannot be safely completed in the same run, stop at the cleanest validated boundary and report exactly what remains.

Do not skip tests or quality gates merely to reach the next milestone. Do not silently expand into 0.5 or 0.6.

When working on multiple milestones in one run, keep commits/changes logically separable where practical so each milestone can be reviewed or reverted independently.

## 10. Engineering rules
- Do not create a `.patch` as a substitute for applying the requested work unless explicitly asked. Changes should be applied directly to the repository.
- Pure-text collaboration is preferred for this project unless the user explicitly requests an artifact/file.
## 11. Quality gate
Before declaring each milestone complete:
2. Run relevant unit/instrumentation tests available in the environment.
6. Confirm the milestone boundary was respected.
7. Confirm security/privacy requirements remain intact.
8. Report exactly what changed, what was tested, and any known limitations.
## 12. Agent operating mode
Do not merely generate snippets. Work directly on the repository, inspect existing structure/tooling, implement the requested milestone(s), test them, and leave the repository in a coherent state.
## 13. Current execution target
**Start from the actual state of `main`.** The historical 0.1 work is complete and the repository is currently at 0.2.
The next agent run should target **Milestone 0.3 and then Milestone 0.4** in sequence. If both can be completed safely, finish both in the same run. If not, stop at a validated milestone boundary rather than leaving half-implemented work.
Read this entire `AGENT.md` before modifying the repository, then inspect the repository and toolchain before making changes.
