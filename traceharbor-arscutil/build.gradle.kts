plugins {
    java
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
    implementation(project(":traceharbor-commons"))
}

extra["publishArtifactId"] = "traceharbor-arscutil"
extra["publishVersion"]    = version.toString()

apply(from = rootProject.file("gradle/maven-publish.gradle.kts"))
