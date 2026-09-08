# SAMVIRA

SAMVIRA is a privacy-first Android photo & memory viewer for organization-based
communities. It is a **viewer and memory experience**, not the original-file
management system: media is displayed while access and media policies are
enforced server-side.

> **Privacy by default, sharing by permission.** Seeing a photo does not
automatically mean the viewer may download, share, or retain the original.

This repository currently contains **Milestone 0.2 — Identity & Application
Shell Hardening** on top of the foundation milestone. It establishes secure
installation identity handling, privacy-aware logging/network boundaries, and
CI/test coverage. Product features remain intentionally out of scope.

## Project status

| Area | Status (0.2) |
| --- | --- |
| Compose application shell | ✅ |
| Navigation architecture | ✅ |
| Local persistence abstraction (DataStore) | ✅ |
| Networking abstraction (OkHttp) | ✅ hardened failure boundary |
| Installation identity foundation (Android Keystore) | ✅ hardened |
| Identity provisioning | ✅ concurrency-safe + fail-closed |
| Logging/redaction boundary | ✅ throwable-aware |
| JVM unit tests | ✅ happy + failure/security paths |
| CI (GitHub Actions) | ✅ workflow added |
| Backend / sync / gallery / accounts | 🚫 out of scope |

## Architecture

The code is organized by layer, keeping boundaries clean so implementations can
evolve (e.g. replacing OkHttp or DataStore, or introducing a backend) without
rewriting callers.

```
com.vexcompany.samvira
├── core/        # DI container + logging/redaction boundary
├── domain/      # Business logic and models (installation identity)
├── data/        # Persistence + networking, behind interfaces
│   ├── identity/    # DataStore-backed installation metadata
│   └── network/     # OkHttp-backed NetworkClient
├── security/    # Android Keystore signing key + FLAG_SECURE helper
└── ui/          # Compose theme, navigation, home screen
```

Dependency injection is **manual** (constructor injection via `AppContainer`).
No compile-time DI framework is used yet; one can be introduced later if it
materially improves maintainability.

### Installation identity

SAMVIRA uses an installation-based identity rather than a username/password
account system:

1. On first use, an EC P-256 key pair is generated inside **Android Keystore**.
2. The private key is non-exportable and is never returned by the security
   abstraction; signing happens with the Keystore-held private key.
3. A random installation identifier is generated and persisted via DataStore.
4. The public key is exposed as PEM for future server registration and
   proof-of-possession/signature authentication.
5. Provisioning is serialized so concurrent first-use calls cannot mint
   multiple identifiers for the same installation.

Important properties and limitations:

- The installation identifier is random; it is **not** derived from IMEI,
  serial number, MAC address, advertising ID, or any hardware fingerprint.
- Uninstall / clear-data / factory-reset can create a **new** installation
  identity. Cryptographic installation identity is not a claim of permanent
  physical-device uniqueness.
- Backups are disabled (`android:allowBackup="false"`) so identity/session
  material is not extracted via backup or device transfer.

### Privacy boundaries (foundation)

- View access is modeled as separate from download permission (no download code
  exists yet, but the architecture preserves the distinction).
- `ScreenGuard` wraps `FLAG_SECURE` for future sensitive surfaces. Screenshot
  protection cannot stop external cameras, rooted/modified devices, or other
  out-of-band capture — it is a deterrent, not a security mechanism.
- Logging funnels through `AppLogger`. `Scrubber` redacts authorization
  headers, bearer tokens, key/value credentials, URL userinfo/query secrets,
  and PEM private keys. Throwable messages are sanitized before logcat as well.
- The network client never logs request headers/bodies and returns fixed,
  data-free failure messages. Timeout, cancellation, connectivity, and HTTP
  failures are classified separately.

## Toolchain

- **Kotlin** 2.0.21
- **Gradle** 8.7 (wrapper committed; distribution checksum pinned)
- **Android Gradle Plugin** 8.5.2
- **Jetpack Compose** (BOM 2024.06.00) + Material 3
- **minSdk** 26 · **targetSdk / compileSdk** 34
- **JDK** 17

The Gradle wrapper is committed with `distributionSha256Sum` for supply-chain
safety. The wrapper distribution checksum is pinned in
`gradle/wrapper/gradle-wrapper.properties`.

## Building

```bash
# Build the debug APK
./gradlew assembleDebug

# Run JVM unit tests
./gradlew testDebugUnitTest

# Lint
./gradlew lintDebug

# Run instrumented tests (requires a connected device/emulator)
./gradlew connectedDebugAndroidTest
```

## CI

GitHub Actions (`.github/workflows/ci.yml`) runs on pushes to `main` and the
manual hardening branches, plus pull requests. It builds the debug APK, runs
lint, runs JVM unit tests, and compiles the instrumented test sources. Hosted CI
does not run the instrumented tests because no emulator is configured in this
minimal workflow.

## Contributing / decisions log

- Package/application id is `com.vexcompany.samvira` (`com.vexcompany.samvira`).
- The brand palette is a placeholder and can be replaced without touching
  screen code (screens reference semantic theme colors only).
- Manual DI was chosen over Hilt/Koin to keep the foundation small and
  auditable; revisit when the object graph grows.
- Out of scope for 0.2: Google Drive integration, backend server, organization
  membership UI, login/account system, album/media sync, download approval,
  comments/reactions backend, admin dashboard, WhatsApp integration, complex
  offline sync, analytics, and advertising.
