// The game itself, for both phones: the rules (engine/), the art (art/), the screens and drawing (ui/),
// and the screen state (app/Game.kt). Everything here is plain Kotlin + Compose Multiplatform; the few
// things that differ per phone are `expect` declarations with an Android and an iOS `actual`.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidLibrary {
        namespace = "org.jasonagibson.sproutpals.shared"
        compileSdk = 37
        minSdk = 34
        // the rules' unit tests run on the computer (JVM), as before
        withHostTestBuilder {}
    }

    // iPhone (device) and the iPhone simulator on Apple-silicon Macs; built on a Mac only
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
        }
    }
}
