// =============================================================================
// Unsigned `devLocal` publication for install to ~/.m2 (companion to the signed
// Sonatype publication produced by com.kernelflux.maven.publish).
//
// Apply only AFTER `gradle/maven-publish.gradle` (requires `ext.publishArtifactId`
// and `components["java"]` to be present).
//
// Coordinate stays in sync with the central publication (`com.kernelflux.mobile`).
//
// Ported from gradle/maven-devlocal-publication.gradle as part of Stage 5 of
// docs/plans/2026-04-23-brand-and-modernize-plan.md.
// =============================================================================
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

configure<PublishingExtension> {
    publications {
        create<MavenPublication>("devLocal") {
            groupId = "com.kernelflux.mobile"
            artifactId = project.extra["publishArtifactId"] as String
            version = project.version.toString()
            from(components["java"])
        }
    }
}

afterEvaluate {
    tasks.findByName("signDevLocalPublication")?.enabled = false
}
