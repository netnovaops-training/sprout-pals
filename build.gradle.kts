// Top-level build file. Plugin versions live in gradle/libs.versions.toml.
//   :shared  the game itself (rules, screens, drawing) for Android and iPhone - Kotlin Multiplatform
//   :app     the Android app around it (sound, sensors, notifications, widget, updater)
//   iosApp/  the iPhone app around it (built on a Mac - see .github/workflows/ios.yml)
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
}
