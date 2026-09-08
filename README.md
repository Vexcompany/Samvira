# SAMVIRA

SAMVIRA is a privacy-first Android photo & memory viewer for organization-based
communities. It is a **viewer and memory experience**, not the original-file
management system: media is displayed while access and media policies are
enforced server-side.

> **Privacy by default, sharing by permission.** Seeing a photo does not
> automatically mean the viewer may download, share, or retain the original.

This repository currently contains **Milestone 0.1 — Foundation**. It
establishes the project structure, application identity, toolchain, and the
abstractions that later milestones (organization membership, albums, download
approval, …) will build on. It does **not** yet implement any product feature.

## Project status

| Area | Status (0.1) |
| --- | --- |
| Compose application shell | ✅ |
| Navigation architecture | ✅ |
| Local persistence abstraction (DataStore) | ✅ |
| Networking abstraction (OkHttp) | ✅ |
| Installation identity foundation (Android Keystore) | ✅ |
| Logging/redaction boundary | ✅ |
| CI (GitHub Actions) | ✅ |
| Unit tests | ✅ |
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
2. The private key is **non-exportable** and never leaves the device.
3. A random installation identifier is generated and persisted via DataStore.
4. The public key is exposed (PEM) for future server registration and
   proof-of-possession/signature authentication.

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
- Logging funnels through `AppLogger`, and `Scrubber` redacts common secret
  shapes (authorization headers, tokens, credentials, private keys) as a safety
  net. Callers must still avoid logging sensitive values in the first place.

## Toolchain

- **Kotlin** 2.0.21
- **Gradle** 8.7 (wrapper committed; distribution checksum pinned)
- **Android Gradle Plugin** 8.5.2
- **Jetpack Compose** (BOM 2024.06.00) + Material 3
- **minSdk** 26 · **targetSdk / compileSdk** 34
- **JDK** 17

The Gradle wrapper is committed with `distributionSha256Sum` for
supply-chain safety. To verify it: `sha256sum gradle/wrapper/gradle-wrapper.jar`
should equal `cb0da6751c2b753a16ac168bb354870ebb1e162e9083f116729cec9c781156b8`
(the official Gradle 8.7 wrapper JAR checksum).

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

GitHub Actions (`.github/workflows/ci.yml`) runs on every push and pull
request: it builds the debug APK, runs lint, runs JVM unit tests, and compiles
the instrumented test sources.

## Contributing / decisions log

- Package/application id is `com.vexcompany.samvira` (reverse-DNS of the owning
  GitHub organization). It can be changed before first public release without
  affecting the code structure.
- The brand palette is a placeholder and can be replaced without touching
  screen code (screens reference semantic theme colors only).
- Manual DI was chosen over Hilt/Koin to keep the foundation small and
  auditable; revisit when the object graph grows.
- Out of scope for 0.1 (intentionally not implemented): Google Drive
  integration, backend server, organization membership UI, login/account
  system, album/media sync, download approval, comments/reactions backend,
  admin dashboard, WhatsApp integration, complex offline sync, analytics, and
  advertising.
