# TraceHarbor AGP 8 Upgrade Tracking

## Target Version TraceHarbor

- AGP: `8.2.2`
- Gradle: `8.2.1`
- JDK: `17`
- Kotlin: `1.8.22`
- compileSdk: `34`
- targetSdk: `34`

## Current Baseline

- Main Android build: `AGP 4.1.0` + `Gradle 6.7.1`
- Sample Android build: `AGP 7.2.2` + `Gradle 7.5`
- Main Kotlin plugin: `1.4.32`
- Sample Kotlin plugin: `1.4.32`

## Primary Blockers

- `traceharbor-gradle-plugin` still depends on removed Transform APIs.
- Android modules do not declare `namespace`.
- Publishing scripts still assume legacy variant/task names.
- CI still assumes pre-JDK-17 Android executors.

## Temporary Compatibility Decisions

- Enable `android.enableLegacyVariantApi=true` to keep old variant access available while the plugin migrates.
- Enable `android.newDsl=false` to keep the legacy Android extension implementations available while the plugin still casts to `AppExtension`/`BaseExtension`.
- Disable TraceHarbor trace bytecode injection on AGP 8 temporarily instead of keeping broken Transform API code paths.
- Keep `removeUnusedResources` on the legacy variant path for now, then migrate it after trace instrumentation is rebuilt.
- Use compatibility helpers for package task lookup and APK output discovery instead of hard-coded `packageApplicationProvider` / `outputFile` access.

## Verification Commands

```bash
cd TraceHarbor && ./gradlew help --stacktrace
cd TraceHarbor && ./gradlew publishToMavenLocal --stacktrace
cd samples/sample-android && ./gradlew help --stacktrace
cd samples/sample-android && ./gradlew assembleDebug -PcompileWithSrc=true --stacktrace
```

## Progress

- [x] Move workspace to `TraceHarbor`
- [x] Upgrade root toolchain files
- [x] Add `namespace` for main Android modules and samples
- [~] Update Android DSL hotspots (`lint`, packaging, sample SDK config)
- [~] Migrate `traceharbor-gradle-plugin` AGP APIs
- [ ] Repair sample, publish, and CI flows
