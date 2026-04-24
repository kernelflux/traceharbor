plugins {
    id("kotlin")
    id("java-gradle-plugin")
    // com.gradle.plugin-publish version is pinned via the root buildscript classpath
    // (see root build.gradle.kts → buildscript { dependencies { classpath ... } }),
    // so we must apply it WITHOUT a version here, otherwise Gradle errors with
    // "the plugin is already on the classpath with an unknown version".
    id("com.gradle.plugin-publish")
}

val gradleExtra = (gradle as org.gradle.api.plugins.ExtensionAware).extensions.extraProperties
val kotlinVersion: String = gradleExtra.get("KOTLIN_VERSION").toString()

dependencies {
    implementation(fileTree("libs") { include("*.jar") })
    implementation(gradleApi())
    implementation(project(":traceharbor-commons"))
    implementation(project(":traceharbor-arscutil"))
    implementation("org.ow2.asm:asm:9.6")
    implementation("org.ow2.asm:asm-commons:9.6")
    implementation("org.ow2.asm:asm-util:9.6")
    implementation("com.google.guava:guava:32.1.3-jre")
    implementation("com.android.tools.build:gradle:8.2.2")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    implementation("com.google.code.gson:gson:2.10.1")
}

group   = "com.kernelflux.traceharbor.plugin"
version = rootProject.extra["VERSION_NAME"].toString()

gradlePlugin {
    website.set("https://github.com/kernelflux/traceharbor")
    vcsUrl.set("https://github.com/kernelflux/traceharbor")
    plugins {
        create("traceharborPlugin") {
            id                  = "com.kernelflux.traceharbor.plugin"
            implementationClass = "com.kernelflux.traceharbor.plugin.TraceHarborPlugin"
            displayName         = "TraceHarbor Gradle Plugin"
            description         = "Bytecode trace instrumentation + perf canary build wiring for Android (AGP 8+)."
            // tags is a SetProperty in plugin-publish 1.x — use .set(...) instead of `=`.
            tags.set(listOf("android", "agp", "instrumentation", "asm", "tracing", "performance", "monitoring", "apm"))
        }
    }
}
