plugins {
    java
    // kotlin-gradle-plugin is pinned via root buildscript classpath, so apply
    // without a version (same lesson as Stage 6 batch D + the resource-canary
    // -common port from J2K round 1).
    kotlin("jvm")
}

version = rootProject.extra["VERSION_NAME"].toString()
group = rootProject.extra["GROUP"].toString()

java {
    sourceCompatibility = rootProject.extra["javaVersion"] as JavaVersion
    targetCompatibility = rootProject.extra["javaVersion"] as JavaVersion
}

tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
tasks.withType<Javadoc>().configureEach { options.encoding = "UTF-8" }

dependencies {
    implementation(fileTree("libs") { include("*.jar") })
    implementation(libs.commons.io)
}

extra["publishArtifactId"] = "traceharbor-commons"
extra["publishVersion"]    = version.toString()

apply(from = rootProject.file("gradle/maven-publish.gradle.kts"))
