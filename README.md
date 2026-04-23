# TraceHarbor

TraceHarbor is an Android performance and diagnostics framework derived from Tencent Matrix and being modernized for continued community development.

## Status

This repository is currently in an active migration phase:

- project identity has been renamed from Matrix to TraceHarbor
- upstream BSD licensing has been preserved
- AGP 8 / Gradle 8 compatibility work is in progress

The current AGP 8 migration tracker lives at:

- `docs/plans/2026-04-23-agp8-upgrade-tracking.md`

## Main Modules

- `traceharbor-android-lib`: core Android runtime library
- `traceharbor-gradle-plugin`: build-time plugin and AGP integration
- `traceharbor-trace-canary`: trace, startup, jank, and ANR related features
- `traceharbor-resource-canary`: resource leak and heap-dump analysis modules
- `traceharbor-battery-canary`: battery and background activity monitoring
- `traceharbor-io-canary`: file IO and closeable leak monitoring
- `traceharbor-sqlite-lint`: SQLite quality checks
- `traceharbor-hooks`, `traceharbor-backtrace`, `traceharbor-memguard`: native hook and memory tooling

## Licensing

TraceHarbor remains a derivative work based on Tencent Matrix.

- upstream license and bundled third-party notices are preserved in `LICENSE`
- derivative work and attribution notes are documented in `NOTICE`
- original vs. new copyright ownership is summarized in `COPYRIGHT`

This project is not affiliated with, endorsed by, or maintained by Tencent or the original Matrix maintainers.

## Build Notes

Current target toolchain:

- AGP `8.2.2`
- Gradle `8.2.1`
- JDK `17`
- Kotlin `1.8.22`

Example commands:

```bash
./gradlew help --stacktrace
./gradlew :traceharbor-gradle-plugin:build --stacktrace
```

If Gradle wrapper download fails in CLI, verify local JDK 17 and network/SSL access first.

## Related Notes

- `traceharbor-hprof-analyzer/README.md`: explains why the native HPROF analyzer is used indirectly through Resource Canary
