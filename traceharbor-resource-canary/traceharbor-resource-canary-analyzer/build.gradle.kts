plugins {
    `java-library`
}

// Bumped from 1.7 → 1.8 to consume traceharbor-resource-canary-common, which moved
// to Java 8 when its utils were ported to Kotlin (kotlin 1.8.x requires jvmTarget ≥ 1.8).
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

version = rootProject.extra["VERSION_NAME"].toString()
group = rootProject.extra["GROUP"].toString()

dependencies {
    implementation(fileTree("libs") { include("*.jar") })
    implementation(project(":traceharbor-resource-canary:traceharbor-resource-canary-common"))

    api("com.squareup.haha:haha:2.0.3")
    compileOnly("org.json:json:20180813")
}

extra["publishArtifactId"] = project.property("POM_ARTIFACT_ID").toString()
extra["publishVersion"]    = version.toString()

apply(from = rootProject.file("gradle/maven-publish.gradle.kts"))

// apply(from = rootProject.file("gradle/check.gradle.kts"))
