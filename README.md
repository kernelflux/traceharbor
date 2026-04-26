# TraceHarbor

TraceHarbor is an Android performance and diagnostics framework derived from Tencent Matrix and modernized for continued community development.

## Current Baseline

The repository now runs on a unified modern Android build baseline:

- AGP `8.2.2`
- Gradle `8.2.1`
- JDK `17`
- Kotlin `1.8.22`
- `minSdk = 21`, `targetSdk = 34`, `compileSdk = 34`

Core build conventions have also been centralized:

- plugin versions and dependency coordinates in `gradle/libs.versions.toml`
- root shared properties in `gradle/root-extra-properties.gradle.kts`
- Kotlin/JVM target alignment in `gradle/kotlin-jvm-target-alignment.gradle.kts`

## Modernization Progress

Recent repository-wide improvements include:

- migration to AGP 8 + Gradle 8 compatible build scripts
- root `plugins { alias(libs.plugins...) }` setup (Kotlin DSL + version catalog)
- unified Java compatibility baseline (`javaVersion = 17`) across modules
- unified Android minimum SDK baseline (`minSdkVersion = 21`)
- module publish metadata normalized with `publishArtifactId` in module `build.gradle.kts`
- continued Java-to-Kotlin migration across core library modules

Migration tracking notes:

- `docs/plans/2026-04-23-agp8-upgrade-tracking.md`
- `docs/plans/2026-04-23-brand-and-modernize-plan.md`

## Main Modules

- `traceharbor-android-lib`: core Android runtime library and plugin lifecycle APIs
- `traceharbor-gradle-plugin`: build-time bytecode/plugin integration
- `traceharbor-trace-canary`: startup, frame/jank, and ANR monitoring
- `traceharbor-resource-canary`:
  - `traceharbor-resource-canary-android`: Android runtime integration
  - `traceharbor-resource-canary-common`: shared analyzer model/helpers
  - `traceharbor-resource-canary-analyzer`: heap analysis engine
  - `traceharbor-resource-canary-analyzer-cli`: CLI analyzer tooling
- `traceharbor-battery-canary`: battery and background activity monitoring
- `traceharbor-io-canary`: file IO and closeable leak monitoring
- `traceharbor-sqlite-lint`: SQLite lint SDK (`full` + `no-op` publications)
- `traceharbor-hooks`, `traceharbor-backtrace`, `traceharbor-memguard`, `traceharbor-mallctl`, `traceharbor-fd`: native hook and memory toolchain components
- `traceharbor-traffic`: traffic/network diagnostics extension

## Quick Start

### Environment

- Install JDK 17 and ensure `java -version` reports 17
- Use the checked-in Gradle wrapper (`./gradlew`)

### Validate the Build

```bash
./gradlew help
```

### Build Sample App

```bash
./gradlew :samples:sample-android:assembleDebug
./gradlew :samples:sample-android:installDebug
```

Notes:

- the sample module can be skipped by build inputs (`TRACEHARBOR_SKIP_SAMPLE=1` or `-PtraceharborSkipSample=true`)
- on publish-like tasks, sample inclusion is auto-skipped in `settings.gradle.kts`

### Build Key Library Modules

```bash
./gradlew :traceharbor-android-lib:assemble
./gradlew :traceharbor-resource-canary:traceharbor-resource-canary-analyzer-cli:build
```

## Publishing

Publishing is centralized via module metadata + shared script wiring:

- modules set `publishArtifactId` (and optional additional publications)
- shared publish logic is applied from `gradle/maven-publish.gradle.kts`

For full publishing notes and credential keys, see:

- `docs/maven-publish.md`

## Licensing and Attribution

TraceHarbor remains a derivative work based on Tencent Matrix.

- upstream license and third-party notices are preserved in `LICENSE`
- derivative work and attribution notes are documented in `NOTICE`
- ownership summary is tracked in `COPYRIGHT`

This project is not affiliated with, endorsed by, or maintained by Tencent or the original Matrix maintainers.

## Related Docs

- `traceharbor-hprof-analyzer/README.md`: native HPROF analyzer relationship to Resource Canary
