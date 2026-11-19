// Top-level build file. Plugin versions live in gradle/libs.versions.toml.

// The Android build plugin brings these libraries onto the BUILD machine (they never ship in the
// app). Their older versions have security advisories (Dependabot), so the build uses at least
// the fixed ones. Remove a line once the plugin's own version is past it.
buildscript {
    dependencies {
        constraints {
            classpath("org.bouncycastle:bcprov-jdk18on:1.87") { because("advisories fixed in 1.84/1.87") }
            classpath("org.bouncycastle:bcpkix-jdk18on:1.87") { because("advisory fixed in 1.84; keep with bcprov") }
            classpath("org.bouncycastle:bcutil-jdk18on:1.87") { because("keep with bcprov") }
            classpath("org.bitbucket.b_c:jose4j:0.9.7") { because("DoS via compressed JWE, fixed in 0.9.7") }
            classpath("org.jdom:jdom2:2.0.6.1") { because("XXE, fixed in 2.0.6.1") }
            classpath("org.apache.commons:commons-lang3:3.22.0") { because("uncontrolled recursion, fixed in 3.22.0") }
            // AGP 9.5.1 brings guava 33.4.0-jre (checked with ./gradlew buildEnvironment, 2026-11-19).
            // Dependabot cannot raise a transitive version itself (its security update job failed with
            // security_update_dependency_not_found), so the constraint does it here.
            classpath("com.google.guava:guava:33.7.2-jre") { because("uncontrolled memory allocation on deserialization, fixed in 33.7.2") }
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
