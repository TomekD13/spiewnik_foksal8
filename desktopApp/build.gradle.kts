import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material)
    implementation(libs.gson)
    implementation(libs.pdfbox)
    implementation(libs.kotlinx.coroutines.swing)

    testImplementation(libs.junit)
}

// piesni.json + Spiewnik.pdf from the shared-assets/ folder are packaged as resources
sourceSets {
    main {
        resources.srcDir("../shared-assets")
    }
}

// Wersja instalatora z numeru buildu CI (-PbuildNumber); lokalnie 1.0.0.
val desktopBuildNumber = (project.findProperty("buildNumber") as String?)?.toIntOrNull()
    ?: System.getenv("BUILD_NUMBER")?.toIntOrNull()
    ?: 0

compose.desktop {
    application {
        mainClass = "com.spiewnik.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Spiewnik"
            packageVersion = "1.0.$desktopBuildNumber"
            description = "Śpiewnik KADS Foksal 8"
            windows {
                iconFile.set(project.file("icons/spiewnik.ico"))
                menuGroup = "Spiewnik"
                // Stałe UUID -> kolejne wersje instalują się jako aktualizacja w miejscu.
                upgradeUuid = "7c3a1f2e-5b9d-4e8a-9f1c-2d6b8a4e0c11"
                perUserInstall = true
                dirChooser = true
                shortcut = true
                menu = true
            }
        }
    }
}
