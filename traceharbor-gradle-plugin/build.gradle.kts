plugins {
    id("kotlin")
    id("java-gradle-plugin")
    // Version is provided by the root plugins block (apply false).
    id("com.gradle.plugin-publish")
}
val javaVersion = rootProject.extra["javaVersion"] as JavaVersion
java {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

dependencies {
    implementation(fileTree("libs") { include("*.jar") })
    implementation(gradleApi())
    implementation(project(":traceharbor-commons"))
    implementation(project(":traceharbor-arscutil"))
    implementation(libs.asm)
    implementation(libs.asm.commons)
    implementation(libs.asm.util)
    implementation(libs.guava)
    implementation(libs.agpGradle)
    implementation(libs.kotlin.stdlib)
    implementation(libs.gson)
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
