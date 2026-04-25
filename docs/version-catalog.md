# Version Catalog (`gradle/libs.versions.toml`)

**Location**: `gradle/libs.versions.toml`
**Accessor name**: `libs` (Gradle 8.x default — picked up automatically without
`dependencyResolutionManagement.versionCatalogs.create("libs")`).

> Stage 4 of [`docs/plans/2026-04-23-brand-and-modernize-plan.md`](plans/2026-04-23-brand-and-modernize-plan.md).
> The catalog has been **created** but module `build.gradle` files have not yet
> been migrated to use it. They still hard-code dep strings. Migration to the
> catalog will happen as part of **Stage 6 (Groovy → KTS)** so a module is
> rewritten exactly once.

---

## Sections

The TOML file is split into 5 well-known top-level tables:

| Table        | Purpose                                                                |
|--------------|------------------------------------------------------------------------|
| `[versions]` | Version literals (string only)                                         |
| `[libraries]`| Maven coordinates → catalog alias                                      |
| `[plugins]`  | Plugin id → version mapping (used with `alias(libs.plugins.xxx)`)      |
| `[bundles]`  | Convenience grouping of multiple library aliases                       |

## Consumption examples (KTS)

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.gson)
    testImplementation(libs.junit)
    androidTestImplementation(libs.bundles.androidxTestNew)
}
```

## Consumption examples (Groovy — interim)

```groovy
plugins {
    alias libs.plugins.android.library
    alias libs.plugins.kotlin.android
}

dependencies {
    implementation libs.androidx.appcompat
    implementation libs.gson
    testImplementation libs.junit
    androidTestImplementation libs.bundles.androidxTestNew
}
```

## Naming convention

- **Library alias** uses `kebab-case`. The dotted accessor in build scripts is
  derived by Gradle automatically: `androidx-appcompat` → `libs.androidx.appcompat`.
- **Suffixes** (`-legacy`, `-new`, `-test`) are used for the *current
  duplicates* (gson 2.8.6 / 2.8.9 / 2.10.1, espresso 3.1.0 / 3.4.0, …). These
  will be collapsed in a follow-up `chore(deps): unify` PR.

## Adding a new dependency

1. Add the version to `[versions]` (`fooLib = "1.2.3"`).
2. Add the alias to `[libraries]`
   (`foo = { module = "com.acme:foo", version.ref = "fooLib" }`).
3. Reference it in the module: `implementation libs.foo` (Groovy)
   or `implementation(libs.foo)` (KTS).
4. Run `./gradlew :module:dependencies --configuration compileClasspath` to
   verify the coordinate resolves.

## What lives **outside** the catalog (intentional)

- The TraceHarbor in-tree plugin id `com.kernelflux.traceharbor.plugin` is
  resolved via `pluginManagement` in `settings.gradle`, **not** via the
  catalog. Reason: its version comes from `gradle.properties`
  (`VERSION_NAME_PREFIX` + `VERSION_NAME_SUFFIX`) so a release bumps a single
  source.
- Native build versions (CMake / NDK) live in `local.properties` and the
  `androidNamespaces` map in the root `build.gradle` — they're not Maven
  coordinates and don't fit the catalog model.

## Open follow-ups

- [ ] Stage 6: rewrite each module `build.gradle.kts` to use catalog accessors.
- [ ] Follow-up PR: collapse duplicate gson / junit / espresso versions.
- [ ] Follow-up PR: bump mockito 2.8.9 → 5.x for full JDK 17 compatibility.
