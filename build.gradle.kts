// =============================================================================
// Root build script — Kotlin DSL.
//
// Ported from build.gradle as part of Stage 5 of
// docs/plans/2026-04-23-brand-and-modernize-plan.md.
//
// All sub-projects are still Groovy `build.gradle` files; they read shared
// state via `rootProject.ext.X` (Groovy MOP automatically resolves to the
// `extra` properties we set below) and `gradle.KOTLIN_VERSION` (likewise).
// =============================================================================
import com.android.build.gradle.LibraryExtension
import org.gradle.api.JavaVersion

buildscript {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    dependencies {
        @Suppress("AndroidGradlePluginVersion")
        classpath("com.android.tools.build:gradle:8.2.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.22")
        classpath("com.kernelflux.maven.publish:maven-central-gradle-plugin:1.0.10")
        classpath("com.gradle.publish:plugin-publish-plugin:1.3.1")
    }
}

// Load repo-local secrets (Sonatype, GPG, Plugin Portal, …) once per build.
// All projects can read via `gradle.privateProperties.getProperty(...)`.
apply(from = rootProject.file("gradle/private-properties.gradle.kts"))

// Mirror a few well-known versions onto `gradle.ext` so still-Groovy module
// scripts can interpolate `"...:${gradle.KOTLIN_VERSION}"` etc. unchanged.
val gradleExtra = (gradle as org.gradle.api.plugins.ExtensionAware).extensions.extraProperties
gradleExtra.set("KOTLIN_VERSION", "1.8.22")
gradleExtra.set("MAVEN_CENTRAL_UPLOADER_VERSION", "1.0.10")
gradleExtra.set("PLUGIN_PUBLISH_VERSION", "1.3.1")

allprojects {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }

    tasks.withType<Javadoc>().configureEach {
        // TODO: re-enable Javadoc generation once warnings are addressed.
        enabled = false
        options.encoding = "UTF-8"
    }

    plugins.withId("com.android.library") {
        extensions.configure(LibraryExtension::class.java) {
            publishing {
                singleVariant("release") {}
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Kotlin 1.8.x dropped jvmTarget=1.7 and now defaults to JVM 17 if you don't
    // set it, but most modules in this repo keep Java targetCompatibility = 1.8.
    // The mismatch trips:
    //   "'compileXxxJavaWithJavac' task (current target is 1.8) and
    //    'compileXxxKotlin' task (current target is 17) jvm target compatibility
    //    should be set to the same Java version."
    //
    // We can't pin a single jvmTarget at the root: traceharbor-gradle-plugin must
    // run on Java 11+ (transitively pulls AGP 8.x which is built for Java 11),
    // while traceharbor-commons / -resource-canary-common / -android libraries
    // stay on Java 1.8 for max device coverage.
    //
    // So instead of hard-coding a value, derive Kotlin's jvmTarget from whatever
    // Java target the module actually uses:
    //   • Pure JVM modules → JavaPluginExtension.targetCompatibility
    //   • Android library  → android.compileOptions.targetCompatibility
    //                        (these don't go through JavaPluginExtension at all,
    //                        so the JVM-only branch returns the JDK default 17,
    //                        which is wrong for an Android lib pinned to 1.8).
    // Done lazily via afterEvaluate so the per-module configuration runs first.
    // ---------------------------------------------------------------------------
    val alignKotlinJvmTarget = Action<Project> {
        afterEvaluate {
            val androidExt = extensions.findByType(LibraryExtension::class.java)
            val javaTarget: String = when {
                androidExt != null ->
                    androidExt.compileOptions.targetCompatibility.toString()
                else ->
                    extensions.findByType(JavaPluginExtension::class.java)
                        ?.targetCompatibility
                        ?.toString()
                        ?: "1.8"
            }
            tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
                kotlinOptions {
                    jvmTarget = javaTarget
                }
            }
        }
    }
    plugins.withId("kotlin-android")               { alignKotlinJvmTarget.execute(this@allprojects) }
    plugins.withId("org.jetbrains.kotlin.android") { alignKotlinJvmTarget.execute(this@allprojects) }
    plugins.withId("org.jetbrains.kotlin.jvm")     { alignKotlinJvmTarget.execute(this@allprojects) }
    plugins.withId("kotlin")                       { alignKotlinJvmTarget.execute(this@allprojects) }
}

// ---------------------------------------------------------------------------
// Project-wide extra properties.
//
// Kept on `rootProject.extra` because every still-Groovy module reads these
// via `rootProject.ext.X` (and Groovy MOP transparently resolves to extra).
// Once Stage 6 (modules -> KTS) lands, consumers will switch to typed
// accessors and these can be lifted into proper top-level vals.
// ---------------------------------------------------------------------------
extra["minSdkVersion"]                    = 19
extra["targetSdkVersion"]                 = 34
extra["compileSdkVersion"]                = 34
extra["buildToolsVersion"]                = "34.0.0"
extra["androidNamespaces"]                = mapOf(
    ":traceharbor-android-commons"                                      to "com.kernelflux.traceharbor.android.commons",
    ":traceharbor-android-lib"                                          to "com.kernelflux.traceharbor",
    ":traceharbor-trace-canary"                                         to "com.kernelflux.traceharbor.trace",
    ":traceharbor-io-canary"                                            to "com.kernelflux.traceharbor.iocanary",
    ":traceharbor-sqlite-lint:traceharbor-sqlite-lint-android-sdk"      to "com.kernelflux.traceharbor.sqlitelint",
    ":traceharbor-battery-canary"                                       to "com.kernelflux.traceharbor.batterycanary",
    ":traceharbor-opengl-leak"                                          to "com.kernelflux.traceharbor.openglleak",
    ":traceharbor-traffic"                                              to "com.kernelflux.traceharbor.traffic",
    ":traceharbor-backtrace"                                            to "com.kernelflux.traceharbor.backtrace",
    ":traceharbor-backtrace:cxx-static"                                 to "com.kernelflux.traceharbor.backtrace.cxxstatic",
    ":traceharbor-hooks"                                                to "com.kernelflux.traceharbor.hook",
    ":traceharbor-hooks:cxx-static"                                     to "com.kernelflux.traceharbor.hook.cxxstatic",
    ":traceharbor-memguard"                                             to "com.kernelflux.traceharbor.memguard",
    ":traceharbor-mallctl"                                              to "com.kernelflux.traceharbor.mallctl",
    ":traceharbor-fd"                                                   to "com.kernelflux.traceharbor.fd",
    ":traceharbor-memory-canary"                                        to "com.kernelflux.traceharbor.memory.canary",
    ":traceharbor-opengl-leak:cxx-static"                               to "com.kernelflux.traceharbor.openglleak.cxxstatic",
    ":traceharbor-resource-canary:traceharbor-resource-canary-android"  to "com.kernelflux.traceharbor.resource",
)

extra["MIN_SDK_VERSION_FOR_HOOK"]         = 21
extra["VERSION_CODE"]                     = 1
extra["javaVersion"]                      = JavaVersion.VERSION_1_8

extra["GROUP"]                            = "com.kernelflux.mobile"

// VERSION_NAME = ${VERSION_NAME_PREFIX}${VERSION_NAME_SUFFIX} (from gradle.properties).
val versionPrefix = (project.findProperty("VERSION_NAME_PREFIX") as String?).orEmpty()
val versionSuffix = (project.findProperty("VERSION_NAME_SUFFIX") as String?).orEmpty()
extra["VERSION_NAME"]                     = "$versionPrefix$versionSuffix"

extra["POM_PACKAGING"]                    = "pom"
extra["POM_NAME"]                         = "TraceHarbor for Android"
extra["POM_DESCRIPTION"]                  = "TraceHarbor is an Android performance and diagnostics framework derived from Tencent Matrix and modernized for continued community development."

extra["POM_URL"]                          = "https://github.com/kernelflux/traceharbor"
extra["POM_SCM_URL"]                      = "https://github.com/kernelflux/traceharbor.git"
extra["POM_SCM_COMMIT"]                   = ""
extra["POM_ISSUE_URL"]                    = "https://github.com/kernelflux/traceharbor/issues"

extra["POM_LICENCE_NAME"]                 = "BSD License"
extra["POM_LICENCE_URL"]                  = "https://opensource.org/licenses/BSD-3-Clause"
extra["POM_LICENCE_DIST"]                 = "repo"

extra["POM_DEVELOPER_ID"]                 = "TraceHarbor Contributors"
extra["POM_DEVELOPER_NAME"]               = "TraceHarbor Contributors"

extra["ABI_FILTERS"]                      = listOf("armeabi-v7a", "arm64-v8a")
extra["LOGGER_VERSION"]                   = 1.1 // fixme: logger
extra["LIFECYCLE_VERSION"]                = "2.3.1"

// ---------------------------------------------------------------------------
// Helper task: publishes the trio needed by samples/sample-android into
// mavenLocal (commons jar + arscutil jar + gradle plugin marker POM + jar).
// ---------------------------------------------------------------------------
tasks.register("traceharborPublishPluginForSample") {
    group = "traceharbor"
    description = "Publishes traceharbor-commons, traceharbor-arscutil, and traceharbor-gradle-plugin to Maven Local. The plugin module uses java-gradle-plugin, so publishToMavenLocal emits both the jar (pluginMaven) and the Plugin Marker POM in one shot — exactly what `plugins { id \"com.kernelflux.traceharbor.plugin\" }` resolution needs."
    dependsOn(
        ":traceharbor-commons:publishDevLocalPublicationToMavenLocal",
        ":traceharbor-arscutil:publishDevLocalPublicationToMavenLocal",
        ":traceharbor-gradle-plugin:publishToMavenLocal",
    )
}

// ---------------------------------------------------------------------------
// Generate version.txt automatically after each build.
// ---------------------------------------------------------------------------
tasks.register("generateVersionTxt") {
    val versionName = extra["VERSION_NAME"] as String
    val outFile = layout.buildDirectory.file("outputs/version.txt")
    outputs.file(outFile)
    doLast {
        val f = outFile.get().asFile
        f.parentFile.mkdirs()
        f.writeText(versionName)
    }
}
