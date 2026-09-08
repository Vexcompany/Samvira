# SAMVIRA — Master Agent Brief

## 1. Project identity

**Project name:** SAMVIRA  
**Repository:** Vexcompany/Samvira  
**Purpose:** Build a privacy-first Android photo/memory viewer for organization-based communities.

SAMVIRA is a public-facing product. Do not expose or document any private/personal story behind the name. Treat `SAMVIRA` as the complete public brand identity.

## 2. Core product vision

SAMVIRA is primarily a **viewer and memory experience**, not the organization's original-file management system.

The authoritative/original media management system is external (currently envisioned as Pagaska Drive). SAMVIRA should display authorized media while enforcing server-side access and media policies.

Core principle:

> **Privacy by default, sharing by permission.**

Seeing a photo does not automatically mean the viewer may download, share, or retain the original.

## 3. Product direction

The long-term product should support:

- Organization-based access.
- Albums and timelines.
- Authorized photo/video viewing.
- Search and discovery.
- Reactions/likes/comments where enabled.
- Per-media or per-album privacy policies.
- Screenshot protection where appropriate.
- Optional watermarking.
- Controlled original-download requests.
- Auditability and abuse protection.
- A lightweight management/dashboard system for authorized organization heads.

Do **not** implement the entire product in one milestone. Build it incrementally and keep boundaries clean.

## 4. Security model

The intended identity model is installation-based rather than a conventional public username/password account system.

On first installation:

1. Generate an asymmetric key pair using Android Keystore.
2. Keep the private key non-exportable in Keystore.
3. Register the public key with the server together with a random installation/device identifier.
4. Authenticate sensitive requests using proof of possession/signatures/challenges as appropriate.
5. The server is the source of truth for authorization and policy.

Important limitations:

- Do not use IMEI, serial number, MAC address, advertising ID, or hardware fingerprinting as identity.
- An uninstall/clear-data/factory-reset may create a new installation identity. Do not pretend cryptographic installation identity guarantees permanent physical-device uniqueness.
- A device should eventually be limited to a maximum of **2 organizations** server-side.
- IP address may be used only as a secondary rate-limit/anti-abuse signal; never treat it as a unique device identity.

## 5. Privacy requirements

Default behavior must be conservative:

- View access is separate from download permission.
- Original downloads are disabled unless explicitly allowed/approved.
- Raw external storage URLs must never be exposed to unauthorized clients.
- Never leak Google Drive/direct-origin URLs when the policy says they should remain hidden.
- Sensitive viewing surfaces should support Android `FLAG_SECURE` where appropriate.
- Clearly document that screenshot protection cannot stop external cameras, rooted/modified devices, or other out-of-band capture.
- Watermarks may be used as a deterrent, not as a cryptographic security mechanism.

Example policy model:

- Public/event/upacara media: screenshot/download may be allowed.
- Personal/member memory media: screenshot/download may be denied.

## 6. Download architecture (future milestone)

Do not implement this early, but preserve architecture for it.

Expected flow:

1. Viewer requests an original.
2. Server creates a request ID.
3. Authorized organization head reviews the request.
4. If approved, server issues a short-lived, single-use, device-bound download token.
5. Token expires or becomes invalid after use.
6. Download is auditable.

Avoid universal/master download keys.

WhatsApp or other messaging channels may later be used for notifications, but must never be the security mechanism itself.

## 7. Architecture direction

Use a modern Android stack centered around:

- Kotlin.
- Jetpack Compose.
- AndroidX.
- Clear separation of UI, domain/business logic, data/network, and platform/security concerns.
- Dependency injection only where it materially improves maintainability; avoid unnecessary framework complexity.
- Repository interfaces around persistence and networking so implementations can evolve.
- Local persistence for installation/session/profile/cache state where appropriate.
- Android Keystore abstraction isolated behind a small security component.

The backend should remain lightweight. A future server may use Go + SQLite + WebSocket or another similarly lightweight design.

Do not couple the Android app tightly to a particular backend implementation.

## 8. Milestone strategy

Development is milestone/version based. Each milestone must leave the repository buildable and auditable.

The current baseline is **Milestone 0.2 — Identity & Application Shell Hardening**. Do not regress to 0.1. The next work begins at 0.3.

### Milestone 0.1 — Foundation

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

- Inspect first, then modify.
- Never overwrite existing work blindly.
- Preserve working code unless there is a concrete reason to change it.
- Prefer small, understandable modules over premature abstraction.
- Avoid giant files and god classes.
- Keep security-sensitive operations isolated and testable.
- Never hardcode API keys, passwords, tokens, private keys, or organization credentials.
- Never commit secrets.
- Never use production credentials in tests.
- Never log private keys, access tokens, raw authorization headers, or sensitive media URLs.
- Validate server responses and handle malformed/unexpected data safely.
- Fail closed for authorization/security decisions.
- Do not silently weaken privacy controls to make a feature work.
- Keep future backend replacement possible through interfaces/contracts.
- Do not create a `.patch` as a substitute for applying the requested work unless explicitly asked. Changes should be applied directly to the repository.
- Pure-text collaboration is preferred for this project unless the user explicitly requests an artifact/file.

## 11. Quality gate

Before declaring each milestone complete:

1. Build the project.
2. Run relevant unit/instrumentation tests available in the environment.
3. Fix compile errors and obvious warnings introduced by the work.
4. Inspect the final diff.
5. Confirm no secrets were added.
6. Confirm the milestone boundary was respected.
7. Confirm security/privacy requirements remain intact.
8. Report exactly what changed, what was tested, and any known limitations.

If a requirement is ambiguous, make the smallest reversible decision, document it, and continue rather than inventing a large subsystem.

## 12. Agent operating mode

Act as a senior Android engineer and security-conscious product engineer.

Do not merely generate snippets. Work directly on the repository, inspect existing structure/tooling, implement the requested milestone(s), test them, and leave the repository in a coherent state.

When a better architecture is discovered during implementation:

- Prefer the simpler design if it satisfies the requirements.
- Explain meaningful architectural deviations.
- Do not expand scope silently.

The user will use other AI agents and ChatGPT for review, so leave code readable and easy to audit.

## 13. Current execution target

**Start from the actual state of `main`.** The historical 0.1 work is complete and the repository is currently at 0.2.

The next agent run should target **Milestone 0.3 and then Milestone 0.4** in sequence. If both can be completed safely, finish both in the same run. If not, stop at a validated milestone boundary rather than leaving half-implemented work.

Read this entire `AGENT.md` before modifying the repository, then inspect the repository and toolchain before making changes.
