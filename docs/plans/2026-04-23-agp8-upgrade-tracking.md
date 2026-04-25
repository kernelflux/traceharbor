# TraceHarbor AGP 8 Upgrade Tracking

## Target Version TraceHarbor

- AGP: `8.2.2`
- Gradle: `8.2.1`
- JDK: `17`
- Kotlin: `1.8.22`
- compileSdk: `34`
- targetSdk: `34`

## Current Baseline

- Main Android build: **AGP 8.2.2 + Gradle 8.2.1** (was AGP 4.1.0 + Gradle 6.7.1)
- Sample Android build: **AGP 8.2.2 + Gradle 8.2.1** (was AGP 7.2.2 + Gradle 7.5)
- Kotlin plugin: **1.8.22** (was 1.4.32)

## Resolved Items

- `traceharbor-gradle-plugin` trace pipeline migrated off the removed `Transform` API to the AGP 8 `AndroidComponents` / `ScopedArtifacts` API. See `TraceHarborTraceAgp8Registrar` + `TraceHarborTraceAgp8Task` + `TraceHarborAgp8TraceRunner`.
- All Android modules declare `namespace`.
- Publishing scripts cleaned up: `gradle/maven-publish.gradle` is now fluxrouter-aligned, with `gradle/maven-devlocal-publication.gradle` for unsigned local installs.
- Sample wired to apply the plugin via `mavenLocal()` coordinates: run `./gradlew traceharborPublishPluginForSample` once before building the sample.
- Removed dead WeChat scripts (`WeChatDebugStub.gradle`, `WeChatDebugStub2.gradle`, `WeChatArmeabiCompat.gradle`).
- Renamed `WeChatNativeDepend.gradle` → `TraceHarborNativeDepend.gradle` and removed unused `WX_BUILD_*` / `wechatPublish` build-info generation.
- Removed dead pre-AGP-8 trace stubs (`TraceHarborTraceTransform.kt`, `TraceHarborTraceLegacyTransform.kt`, `TraceHarborTraceInjection.kt`, `TraceHarborTraceCompat.kt`, the commented-out Java `TraceHarborTraceTransform.java`).
- Removed `android.enableLegacyVariantApi=true` and `android.newDsl=false` from `gradle.properties` — no longer needed for the trace path.

## Known Follow-ups

- `removeUnusedResources` extension still uses the legacy `applicationVariants` / `BaseVariant` path through `TraceHarborRemoveUnusedRegistrar`. It is opt-in (`removeUnusedResources { enable = true }`, default `false`) and emits a warning on AGP 8. Migrate it to AGP 8 `Variant` APIs when a real consumer needs it.
- A handful of "Configuration was resolved during configuration time" warnings remain (caused by the kfx publish plugin reading classpaths early). Not fatal.

## Verification Commands

```bash
cd traceharbor
TRACEHARBOR_SKIP_SAMPLE=1 ./gradlew traceharborPublishPluginForSample
./gradlew :samples:sample-android:assembleDebug
./gradlew :samples:sample-android:transformDebugClassesWithTraceHarbor
```

## Progress

- [x] Move workspace to `TraceHarbor`
- [x] Upgrade root toolchain files
- [x] Add `namespace` for main Android modules and samples
- [x] Update Android DSL hotspots (`lint`, packaging, sample SDK config)
- [x] Migrate `traceharbor-gradle-plugin` AGP APIs (trace path)
- [x] Repair sample, publish, and CI flows
- [ ] Migrate `removeUnusedResources` to AGP 8 `Variant` APIs (opt-in feature, follow-up)
