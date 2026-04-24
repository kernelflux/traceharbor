plugins {
    java
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
    implementation("commons-io:commons-io:2.6")
}

extra["publishArtifactId"] = project.property("POM_ARTIFACT_ID").toString()
extra["publishVersion"]    = version.toString()

apply(from = rootProject.file("gradle/maven-publish.gradle.kts"))
apply(from = rootProject.file("gradle/maven-devlocal-publication.gradle.kts"))
