plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "org.jasonagibson.sproutpals"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "org.jasonagibson.sproutpals"
        minSdk = 34
        targetSdk = 36
        // Bump on every release: the updater compares this number with version.json.
        versionCode = 1
        versionName = "0.1"

        // Where the game lives online: self-update files and the friends API.
        // Override for a test server with  ./gradlew -PserverBase=https://example.test/game ...
        val serverBase = (project.findProperty("serverBase") as String?) ?: "https://jasonagibson.zapto.org/game"
        buildConfigField("String", "SERVER_BASE", "\"$serverBase\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // For BuildConfig.VERSION_CODE (updater) and BuildConfig.DEBUG (time warp).
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
