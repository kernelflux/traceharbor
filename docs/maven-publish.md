# TraceHarbor Maven Publish

TraceHarbor now uses a single Maven Central upload flow modeled on the `aether` repository.

## Unified Publish Path

All publishable modules now set:

- `publishArtifactId`
- `publishVersion`

and then apply:

- `gradle/maven-publish.gradle`

There is no longer an `Internal` vs `External` publish split in module build files.

## Credentials

Publishing reads credentials from Gradle properties first, then from `private.properties` in the repo root.

Supported keys:

- `sonatypeUsername`
- `sonatypePassword`
- `signingKeyFile`
- `signingPass`

`private.properties` is gitignored and is the recommended local place for these values.

## Signing

- `signingKeyFile` can be absolute or relative to the repo root
- if the signing key file is missing, the upload script logs that signing is disabled

## Main Publishable Modules

Java artifacts:

- `:traceharbor-commons`
- `:traceharbor-gradle-plugin`
- `:traceharbor-arscutil`
- `:traceharbor-apk-canary`
- `:traceharbor-resource-canary:traceharbor-resource-canary-common`
- `:traceharbor-resource-canary:traceharbor-resource-canary-analyzer`

Android artifacts:

- `:traceharbor-android-commons`
- `:traceharbor-android-lib`
- `:traceharbor-trace-canary`
- `:traceharbor-io-canary`
- `:traceharbor-battery-canary`
- `:traceharbor-memory-canary`
- `:traceharbor-traffic`
- `:traceharbor-fd`
- `:traceharbor-mallctl`
- `:traceharbor-backtrace`
- `:traceharbor-opengl-leak`
- `:traceharbor-memguard`
- `:traceharbor-hooks`
- `:traceharbor-resource-canary:traceharbor-resource-canary-android`
- `:traceharbor-sqlite-lint:traceharbor-sqlite-lint-android-sdk`

`traceharbor-sqlite-lint-android-sdk` keeps two Maven publications from the same module:

- `traceharbor-sqlite-lint-android-sdk` from `fullRelease`
- `traceharbor-sqlite-lint-android-sdk-no-op` from `stubRelease`

Optional static Android artifacts:

- `:traceharbor-hooks:cxx-static`
- `:traceharbor-opengl-leak:cxx-static`
- `:traceharbor-backtrace:cxx-static`

Non-published tool module:

- `:traceharbor-resource-canary:traceharbor-resource-canary-analyzer-cli`

## Validation Suggestions

Start with one Java module and one Android module:

```bash
./gradlew :traceharbor-commons:tasks --all
./gradlew :traceharbor-android-lib:tasks --all
```

Then inspect uploader-related tasks exposed by `com.kernelflux.maven.publish` and run the corresponding Maven Central upload workflow with local credentials in place.
