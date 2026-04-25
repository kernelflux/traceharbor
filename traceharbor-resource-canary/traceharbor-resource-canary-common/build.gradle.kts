plugins {
    java
    // kotlin-gradle-plugin is pinned via root buildscript classpath
    // (see root build.gradle.kts → buildscript { dependencies { classpath ... } }),
    // so we apply it WITHOUT a version here.
    kotlin("jvm")
}

// Bumped from 1.7 → 1.8: Kotlin 1.8.x requires jvmTarget ≥ 1.8.
// Same constraint forced the bump in traceharbor-memory-canary.
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

version = rootProject.extra["VERSION_NAME"].toString()
group = rootProject.extra["GROUP"].toString()

dependencies {
    implementation(fileTree("libs") { include("*.jar") })
}

extra["publishArtifactId"] = project.property("POM_ARTIFACT_ID").toString()
extra["publishVersion"]    = version.toString()

apply(from = rootProject.file("gradle/maven-publish.gradle.kts"))
