# SAMVIRA — Master Agent Brief

## 1. Project identity

**Project:** SAMVIRA  
**Repository:** Vexcompany/Samvira  
**Purpose:** privacy-first Android photo/memory viewer for organization-based communities.

SAMVIRA is a viewer and memory experience, not the original-file management system. Pagaska Drive is the planned authoritative/original media provider. Core principle: **Privacy by default, sharing by permission.**

## 2. Security and privacy model

- Installation identity uses a random installation ID plus a non-exportable EC P-256 key in Android Keystore.
- Never use IMEI, serial number, MAC address, advertising ID, or hardware fingerprinting as identity.
- Server is the source of truth for authentication, organization membership, and media authorization.
- View access is separate from download permission.
- Raw provider/original URLs must never be exposed to clients.
- Media is delivered through an authorized, short-lived gateway grant; this prevents direct provider access but does not make client-side saving or external capture technically impossible.
- Use `FLAG_SECURE` where appropriate; document its limitations.
- Never log private keys, access tokens, authorization headers, or sensitive provider URLs.
- Fail closed for authorization and privacy decisions.

## 3. Architecture

Android stack: Kotlin, Jetpack Compose, AndroidX, repository/network abstractions, local persistence, and isolated Android Keystore security components. Keep backend/provider implementations replaceable behind interfaces.

Backend should remain lightweight and auditable. Avoid unnecessary framework complexity and giant modules.

## 4. Milestones

### 0.1 — Foundation — completed
Android project, Compose shell, navigation, persistence/network abstractions, Keystore identity foundation, logging/error boundaries, tests, CI, and README.

### 0.2 — Identity & Application Shell Hardening — completed
Hardened installation identity, DataStore persistence, networking boundary, logging/redaction, failure handling, tests, and CI.

### 0.3 — Backend Connectivity & Server Contract — completed
Versioned API contract, typed DTOs/errors, installation registration, challenge/proof-of-possession authentication, secure sessions, persistent backend records, validation, fail-closed authorization, and regression tests.

### 0.4 — Organization & Membership Foundation — completed
Organization/membership models and contracts, server-controlled membership authorization, installation membership bootstrap, maximum 2 organizations per installation, organization selection/context, membership states, cross-organization isolation, and regression tests.

### 0.5 — Pagaska Drive Media Bridge — completed
Provider abstraction, organization-scoped media listing, provider-origin validation, redirect blocking, canonical MIME validation, safe thumbnail/preview gateway delivery, media view grants, session-bounded grant expiry, authorized content streaming, raw provider URL stripping, upstream stream-failure containment, and regression coverage are implemented and validated.

Quality gate completed: Android build/lint/JVM tests/instrumented-test compilation and backend Node test suite are covered by CI, with the final hardening cycle resolving release endpoint validation, provider redirect/MIME boundaries, public-key validation, rate/capacity limits, atomic challenge/session creation, media grant lifetime, and stream failure handling.

Known limitation: the current Pagaska Drive integration is a provider bridge and testable contract, not proof of a deployed production Pagaska Drive service. Production credentials, endpoints, and provider capabilities must be supplied separately and must not be invented in SAMVIRA.

### 0.6+ — Gallery / Timeline / Album Experience
Next target: build the Google Photos-like viewer on top of the validated media bridge: photo/video grid, timeline, albums, search/discovery, media detail viewing, privacy-aware rendering, and appropriate cache/offline behavior. Controlled original-download requests remain a later security-sensitive feature.

## 5. Pagaska Drive boundary

Pagaska Drive is the authoritative/original provider. SAMVIRA must access it through a provider abstraction. Do not leak `source_url` or direct storage URLs to clients. Provider redirects must be rejected or individually validated against an explicit trusted-origin policy. Provider metadata must be validated before entering HTTP response headers.

The current integration is a provider bridge, not proof of a deployed production Pagaska Drive service. Do not invent credentials, endpoints, or provider capabilities.

## 6. Media authorization model

Expected viewing flow:
1. Authenticated installation selects an authorized organization.
2. Server verifies active membership.
3. Client requests a media view grant.
4. Server resolves the media through the provider and creates a short-lived installation/org/media-bound grant.
5. Client requests content through SAMVIRA using the session plus view grant.
6. Server revalidates session, membership, and grant, then streams provider bytes.

Grant expiry must never outlive the authenticated session. Do not expose universal/master media keys.

## 7. Engineering rules

- Inspect first, then modify.
- Preserve working code unless there is a concrete reason to change it.
- Prefer small, understandable modules.
- Keep security-sensitive operations isolated and testable.
- Never commit secrets or production credentials.
- Validate server responses and malformed input safely.
- Do not silently weaken privacy controls to make a feature work.
- Keep future backend/provider replacement possible through interfaces/contracts.
- Apply requested changes directly to the repository; do not create a `.patch` as a substitute unless explicitly requested.
- Pure-text collaboration is preferred unless an artifact is explicitly requested.
- Work directly from the actual state of `main`.

## 8. Quality gate

Before declaring a milestone complete:
1. Build the project.
2. Run relevant unit/instrumentation tests available in the environment.
3. Fix compile errors and obvious introduced warnings.
4. Inspect the final diff.
5. Confirm no secrets were added.
6. Confirm milestone boundaries were respected.
7. Confirm security/privacy requirements remain intact.
8. Report exactly what changed, what was tested, and known limitations.

## 9. Operating mode

Act as a senior Android/security-conscious product engineer. Do not merely generate snippets: inspect the repository, implement changes directly, test them, and leave a coherent, auditable state. When ambiguity exists, choose the smallest reversible design and document it.

**Current target:** Milestone 0.6 — Gallery / Timeline / Album Experience.