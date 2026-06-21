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

compose.desktop {
    application {
        mainClass = "com.spiewnik.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Spiewnik"
            packageVersion = "1.0.0"
            description = "Śpiewnik KADS Foksal 8"
        }
    }
}
