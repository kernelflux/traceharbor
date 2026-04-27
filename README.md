# TraceHarbor

TraceHarbor is an Android performance and diagnostics toolkit derived from
Tencent Matrix and modernized for AGP 8+ projects. It provides runtime canary
modules for common mobile performance problems and a Gradle plugin for
build-time bytecode trace instrumentation.

The Gradle plugin published as `com.kernelflux.traceharbor.plugin` is intended
for Android application projects that want method tracing, startup/jank analysis
support, and optional APK resource analysis wiring without maintaining a custom
Transform-based build integration.

## Features

- AGP 8+ bytecode instrumentation through the Android Components API.
- Startup, frame, jank, touch-lag, thread, and ANR trace support through
  `traceharbor-trace-canary`.
- Inline Gradle DSL for excluding packages or exact classes from trace
  instrumentation.
- Optional APK unused-resource task wiring for projects that still depend on the
  Matrix-style resource shrink workflow.
- Android runtime modules for memory, IO, battery, SQLite, traffic, native hooks,
  backtrace, and related diagnostics.

## Compatibility

TraceHarbor currently uses the following baseline:

- Android Gradle Plugin `8.2.2`
- Gradle `8.2.1`
- JDK `17`
- Kotlin `1.8.22`
- `minSdk = 21`, `targetSdk = 34`, `compileSdk = 34`

The Gradle plugin requires `com.android.application` and AGP `8.0.0` or newer.
Library-only Android modules are not valid plugin targets.

## Installation

Add the TraceHarbor Gradle plugin to an Android application module:

```kotlin
plugins {
    id("com.android.application")
    id("com.kernelflux.traceharbor.plugin") version "0.0.1"
}
```

If your settings file declares plugin repositories explicitly, make sure the
Gradle Plugin Portal is present:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
```

Runtime canary artifacts are published separately. Add only the modules your app
uses:

```kotlin
dependencies {
    implementation("com.kernelflux.mobile:traceharbor-android-lib:0.0.1")
    implementation("com.kernelflux.mobile:traceharbor-trace-canary:0.0.1")
}
```

## Basic Configuration

The plugin contributes a `traceHarbor` extension. Trace instrumentation is
disabled by default, so enable it explicitly in the app module:

```kotlin
traceHarbor {
    logLevel = "I"

    trace {
        isEnable = true
        ignorePackages = mutableListOf(
            "com.example.generated",
            "com.example.thirdparty.**",
        )
        ignoreClasses = mutableListOf(
            "com.example.app.DebugOnlyActivity",
        )
    }
}
```

For Groovy DSL builds:

```groovy
traceHarbor {
    logLevel = "I"

    trace {
        enable true
        ignorePackage "com.example.generated"
        ignoreClass "com.example.app.DebugOnlyActivity"
    }
}
```

`ignorePackages` accepts package prefixes. A trailing `.`, `.*`, or `.**` is
accepted for readability and normalized internally. `ignoreClasses` expects exact
fully qualified class names.

## Optional Resource Task

The `removeUnusedResources` block is available for projects that need the legacy
resource analysis workflow. It is disabled by default:

```kotlin
traceHarbor {
    removeUnusedResources {
        enable = false
        variant = "release"
        shrinkArsc = false
        ignoreResources = setOf("R.string.keep_me")
    }
}
```

This path still uses legacy variant APIs and may be limited on AGP 8. Keep it
disabled unless your project has validated the workflow.

## Build From Source

Use the checked-in Gradle wrapper with JDK 17:

```bash
./gradlew help
./gradlew :traceharbor-gradle-plugin:build
./gradlew :samples:sample-android:assembleDebug
```

The sample module can be skipped with `TRACEHARBOR_SKIP_SAMPLE=1` or
`-PtraceharborSkipSample=true`. Publish-like tasks skip sample inclusion
automatically in `settings.gradle.kts`.

## Main Modules

- `traceharbor-gradle-plugin`: Gradle plugin and bytecode instrumentation wiring.
- `traceharbor-android-lib`: core Android runtime library and plugin lifecycle APIs.
- `traceharbor-trace-canary`: startup, frame/jank, touch-lag, thread, and ANR monitoring.
- `traceharbor-resource-canary`: runtime heap/resource leak support plus analyzer modules.
- `traceharbor-battery-canary`: battery and background activity monitoring.
- `traceharbor-io-canary`: file IO and closeable leak monitoring.
- `traceharbor-sqlite-lint`: SQLite lint SDK with full and no-op publications.
- `traceharbor-hooks`, `traceharbor-backtrace`, `traceharbor-memguard`,
  `traceharbor-mallctl`, and `traceharbor-fd`: native hook and memory tooling.
- `traceharbor-traffic`: traffic and network diagnostics extension.
- `traceharbor-apk-canary`: APK inspection utilities.

## Repository Status

This repository is being modernized from the original Matrix codebase. Recent
work includes AGP 8 and Gradle 8 migration, version catalog adoption, centralized
publish metadata, Java 17 alignment, and ongoing Kotlin migration.

Tracking notes:

- `docs/plans/2026-04-23-agp8-upgrade-tracking.md`
- `docs/plans/2026-04-23-brand-and-modernize-plan.md`

Publishing details for Maven artifacts are documented in `docs/maven-publish.md`.

## License and Attribution

TraceHarbor is a derivative work based on Tencent Matrix.

- Upstream license and third-party notices are preserved in `LICENSE`.
- Derivative work and attribution notes are documented in `NOTICE`.
- Ownership summary is tracked in `COPYRIGHT`.

This project is not affiliated with, endorsed by, or maintained by Tencent or the
original Matrix maintainers.
