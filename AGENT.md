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

Do not implement this in the foundation milestone, but preserve architecture for it.

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

If the repository is empty, prefer a modern Android stack centered around:

- Kotlin.
- Jetpack Compose.
- AndroidX.
- A clear separation of UI, domain/business logic, data/network, and platform/security concerns.
- Dependency injection only where it materially improves maintainability; avoid unnecessary framework complexity.
- Repository interfaces around persistence and networking so implementations can evolve.
- Local persistence for installation/session/profile/cache state where appropriate.
- Android Keystore abstraction isolated behind a small security component.

The backend is expected to remain lightweight. A future server may use Go + SQLite + WebSocket or another similarly lightweight design.

Do not couple the Android app tightly to a particular backend implementation.

## 8. Milestone strategy

Development is milestone/version based. Each milestone must leave the repository buildable.

### Milestone 0.1 — Foundation

Implement **only the foundation**:

- Inspect the repository before changing anything.
- Initialize/repair the Android project if the repository is empty.
- Establish the SAMVIRA application identity and package structure.
- Establish a clean, modular project structure appropriate for future growth.
- Create a minimal Compose-based application shell.
- Establish basic navigation architecture without implementing future product screens.
- Establish abstractions for local persistence and networking.
- Establish the Android Keystore installation-identity foundation behind a clean interface.
- Add sensible error handling/logging boundaries without leaking secrets.
- Add baseline tests for the foundation where practical.
- Add GitHub Actions CI for build/test if appropriate and compatible with the repository.
- Add/update README with setup and development instructions.
- Ensure the project can build successfully.

A simple initial screen may display the SAMVIRA brand and foundation status. It should look intentional, but do not spend the milestone implementing a full visual gallery.

### Explicitly OUT OF SCOPE for 0.1

Do not implement yet:

- Google Drive integration.
- Full backend/server implementation.
- Organization membership UI.
- Full login/account system.
- Album/media synchronization.
- Download approval workflow.
- Comments/reactions backend.
- Admin dashboard.
- WhatsApp integration.
- Complex offline synchronization.
- Analytics/telemetry that is not explicitly required.
- Advertising.
- Any collection of unnecessary personal/device identifiers.

## 9. Engineering rules

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

## 10. Quality gate

Before declaring a milestone complete:

1. Build the project.
2. Run available unit/instrumentation tests that are relevant.
3. Fix compile errors and obvious warnings introduced by the work.
4. Inspect the final diff.
5. Confirm no secrets were added.
6. Confirm future milestones were not accidentally implemented.
7. Report exactly what changed, what was tested, and any known limitations.

If a requirement is ambiguous, make the smallest reversible decision, document it, and continue rather than inventing a large subsystem.

## 11. Agent operating mode

Act as a senior Android engineer and security-conscious product engineer.

Do not merely generate snippets. Work directly on the repository, inspect existing structure/tooling, implement the requested milestone, test it, and leave the repository in a coherent state.

When a better architecture is discovered during implementation:

- Prefer the simpler design if it satisfies the requirements.
- Explain meaningful architectural deviations.
- Do not expand scope silently.

The user will use other AI agents and ChatGPT for review, so leave code readable and easy to audit.

## 12. Immediate instruction

The next agent task should be **Milestone 0.1 only**.

Read this entire `AGENT.md` before modifying the repository.

Then inspect the repository and toolchain, implement Milestone 0.1, build/test it, and report the result.
