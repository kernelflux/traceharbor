plugins {
    id("java")
    kotlin("jvm")
}

val javaVersion = rootProject.extra["javaVersion"] as JavaVersion

java {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

version = rootProject.extra["VERSION_NAME"].toString()
group   = rootProject.extra["GROUP"].toString()

dependencies {
    implementation(fileTree("libs") { include("*.jar") })
    implementation("com.google.code.gson:gson:2.8.9")
    implementation(project(":traceharbor-commons"))
    implementation("com.android.tools:common:25.1.0")
}

// ---------------------------------------------------------------------------
// Fat-jar with Main-Class. Original Groovy script reflectively flipped
// `canBeResolved` on `configurations.implementation` to allow resolving it
// at jar-time, then bundled `configurations.runtime` (which doesn't exist in
// modern Gradle anyway). Replaced with the modern, supported approach: bundle
// `runtimeClasspath`, which is resolvable by design.
// ---------------------------------------------------------------------------
tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "Main-Class"       to "com.kernelflux.traceharbor.apk.ApkChecker",
            "Manifest-Version" to project.version.toString(),
        )
    }
    val runtimeJars = configurations.named("runtimeClasspath")
    from({
        runtimeJars.get().filter { it.exists() }.map { if (it.isDirectory) it else zipTree(it) }
    })
    exclude("META-INF/MANIFEST.MF", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.register<Copy>("buildApkCheckJar") {
    group = "traceharbor"
    dependsOn("build", "jar")
    from("build/libs") {
        include("*.jar")
        exclude("*-javadoc.jar")
        exclude("*-sources.jar")
    }
    into(project.file("tools_output"))
}

extra["publishArtifactId"] = project.property("POM_ARTIFACT_ID").toString()
extra["publishVersion"]    = version.toString()
apply(from = rootProject.file("gradle/maven-publish.gradle.kts"))
