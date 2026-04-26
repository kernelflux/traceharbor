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
    implementation(libs.gson)
    implementation(libs.android.tools.common)

    implementation(project(":traceharbor-commons"))
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

extra["publishArtifactId"] = "traceharbor-apk-canary"
extra["publishVersion"]    = version.toString()
apply(from = rootProject.file("gradle/maven-publish.gradle.kts"))
