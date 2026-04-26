plugins {
    `java-library`
    kotlin("jvm")
}

val javaVersion = rootProject.extra["javaVersion"] as JavaVersion

java {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = javaVersion.toString()
    }
}

version = rootProject.extra["VERSION_NAME"].toString()
group = rootProject.extra["GROUP"].toString()

dependencies {
    implementation(fileTree("libs") { include("*.jar") })
    implementation(project(":traceharbor-resource-canary:traceharbor-resource-canary-common"))

    api(libs.haha)
    compileOnly(libs.org.json)
}

extra["publishArtifactId"] = "traceharbor-resource-canary-analyzer"
extra["publishVersion"]    = version.toString()

apply(from = rootProject.file("gradle/maven-publish.gradle.kts"))

// apply(from = rootProject.file("gradle/check.gradle.kts"))
