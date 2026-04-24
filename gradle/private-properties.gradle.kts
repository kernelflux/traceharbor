// =============================================================================
// Loads repo-local `private.properties` (Sonatype/Maven Central creds, GPG
// signing info, Gradle Plugin Portal `gradle.publish.*`, and any future
// secrets) into a Gradle-wide map, and mirrors a few well-known keys into JVM
// system properties so that third-party plugins (notably
// `com.gradle.plugin-publish`) can pick them up without per-module boilerplate.
//
// Apply once from the root `build.gradle`:
//
//   apply from: rootProject.file('gradle/private-properties.gradle.kts')
//
// Read from any sub-project, with -P / gradle.properties / env overrides
// taking precedence:
//
//   def v = project.findProperty('mySecret')
//                 ?: gradle.privateProperties.getProperty('mySecret')
//
// Notes:
//   - `private.properties` is .gitignored. Missing file = empty Properties (no error).
//   - Loaded only once per build invocation (cached on `gradle.extra`).
//   - System-property mirror is intentional for plugins that read via
//     `providers.systemProperty(...)` and ignore Gradle ext / project properties
//     (e.g. `com.gradle.plugin-publish` 1.x).
//
// Ported from gradle/private-properties.gradle as part of Stage 5 of
// docs/plans/2026-04-23-brand-and-modernize-plan.md.
// =============================================================================
import java.util.Properties

val gradleExtra = (gradle as org.gradle.api.plugins.ExtensionAware).extensions.extraProperties

if (!gradleExtra.has("privateProperties")) {
    val loaded = Properties()
    val f = rootProject.file("private.properties")
    if (f.exists()) {
        f.inputStream().use { loaded.load(it) }
    }
    gradleExtra.set("privateProperties", loaded)

    // ---- Bridge well-known secrets into JVM system properties ----
    // Mapping: <camelCase key in private.properties> -> <dotted system property name>.
    // -D / pre-set system properties always win; we only set when absent.
    val systemPropertyBridge = mapOf(
        // Gradle Plugin Portal (com.gradle.plugin-publish):
        // The plugin reads via providers.systemProperty('gradle.publish.key/secret').
        "gradlePublishKey" to "gradle.publish.key",
        "gradlePublishSecret" to "gradle.publish.secret",
    )
    systemPropertyBridge.forEach { (fromKey, toSysProp) ->
        if (System.getProperty(toSysProp) == null) {
            val v = loaded.getProperty(fromKey)
            if (!v.isNullOrEmpty()) {
                System.setProperty(toSysProp, v)
            }
        }
    }
}
