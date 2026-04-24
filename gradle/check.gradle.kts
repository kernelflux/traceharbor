// =============================================================================
// Checkstyle wiring — Kotlin DSL.
//
// Applied (indirectly) by gradle/maven-publish.gradle so every published module
// gets a `checkstyle` task wired into `build` / `preBuild` (Android) and `check`.
//
// Ported from gradle/check.gradle as part of Stage 5 of
// docs/plans/2026-04-23-brand-and-modernize-plan.md.
// =============================================================================
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension

apply(plugin = "checkstyle")

configure<CheckstyleExtension> {
    configFile = rootProject.file("checkstyle.xml")
    toolVersion = "6.19"
    isIgnoreFailures = false
    isShowViolations = true
}

tasks.register<Checkstyle>("checkstyle") {
    source("src/main/java")
    include("**/*.java")
    include("**/*.kt")
    classpath = files()
}

afterEvaluate {
    tasks.named("build") { dependsOn("checkstyle") }
    if (plugins.hasPlugin("com.android.application") || plugins.hasPlugin("com.android.library")) {
        tasks.named("preBuild") { dependsOn("checkstyle") }
    }
}

tasks.named("check") { dependsOn("checkstyle") }
