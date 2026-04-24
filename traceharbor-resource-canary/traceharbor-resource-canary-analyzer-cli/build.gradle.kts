plugins {
    id("java")
}

// Bumped from 1.7 → 1.8 to consume traceharbor-resource-canary-common, which moved
// to Java 8 when its utils were ported to Kotlin (kotlin 1.8.x requires jvmTarget ≥ 1.8).
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

version = rootProject.extra["VERSION_NAME"].toString()
group   = rootProject.extra["GROUP"].toString()

dependencies {
    implementation("org.json:json:20180813")
    implementation("commons-cli:commons-cli:1.3.1")

    implementation(project(":traceharbor-resource-canary:traceharbor-resource-canary-analyzer"))
    implementation(project(":traceharbor-resource-canary:traceharbor-resource-canary-common"))
}

// ---------------------------------------------------------------------------
// Fat-jar with Main-Class. Same modernization as traceharbor-apk-canary —
// drop the reflective `canBeResolved` hack on the `implementation`
// configuration and bundle `runtimeClasspath` instead, which is resolvable
// by design.
// ---------------------------------------------------------------------------
tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "Main-Class"       to "com.kernelflux.traceharbor.resource.analyzer.CLIMain",
            "Manifest-Version" to archiveVersion.get(),
        )
    }
    val runtimeJars = configurations.named("runtimeClasspath")
    from({
        runtimeJars.get().filter { it.exists() }.map { if (it.isDirectory) it else zipTree(it) }
    })
    exclude("META-INF/MANIFEST.MF", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.register<Copy>("buildResourceCheckJar") {
    group = "traceharbor"
    dependsOn("build", "jar")
    from("build/libs") {
        include("*.jar")
        exclude("*-javadoc.jar")
        exclude("*-sources.jar")
    }
    into(project.file("tools_output"))
}
