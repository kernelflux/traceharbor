plugins {
    java
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_7
    targetCompatibility = JavaVersion.VERSION_1_7
}

version = rootProject.extra["VERSION_NAME"].toString()
group = rootProject.extra["GROUP"].toString()

dependencies {
    implementation(fileTree("libs") { include("*.jar") })
}

extra["publishArtifactId"] = project.property("POM_ARTIFACT_ID").toString()
extra["publishVersion"]    = version.toString()

apply(from = rootProject.file("gradle/maven-publish.gradle.kts"))
