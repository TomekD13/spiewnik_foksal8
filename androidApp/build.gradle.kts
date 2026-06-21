plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.spiewnik.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.spiewnik.app"
        minSdk = 28
        targetSdk = 34

        // Wersja pochodzi z numeru buildu CI (-PbuildNumber lub env BUILD_NUMBER).
        // Lokalny build bez CI -> wersja "1.0.0-dev".
        val buildNumber = (project.findProperty("buildNumber") as String?)?.toIntOrNull()
            ?: System.getenv("BUILD_NUMBER")?.toIntOrNull()
            ?: 0
        versionCode = if (buildNumber > 0) buildNumber else 1
        versionName = if (buildNumber > 0) "1.0.$buildNumber" else "1.0.0-dev"
    }

    signingConfigs {
        // Stable keystore committed to the repo so every build (local + CI) shares one
        // signature — updates install in place, no uninstall needed. Sideload only:
        // this is NOT a Play Store key, the password is intentionally public.
        create("sideload") {
            storeFile = file("sideload.jks")
            storePassword = "spiewnik123"
            keyAlias = "spiewnik"
            keyPassword = "spiewnik123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sideload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    androidResources {
        noCompress += "pdf"
    }

    // piesni.json + Spiewnik.pdf live in the shared-assets/ folder (single source of
    // truth for both apps) and are picked up here as an extra assets source dir.
    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets", "../shared-assets")
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.activity.ktx)
    implementation(libs.fragment.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
