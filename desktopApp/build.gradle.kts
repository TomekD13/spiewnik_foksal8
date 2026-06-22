import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// Wersja z numeru buildu CI (-PbuildNumber / env BUILD_NUMBER); lokalnie "1.0.0-dev"
// (spójnie z androidowym versionName).
val desktopBuildNumber = (project.findProperty("buildNumber") as String?)?.toIntOrNull()
    ?: System.getenv("BUILD_NUMBER")?.toIntOrNull()
    ?: 0
val desktopVersionName = if (desktopBuildNumber > 0) "1.0.$desktopBuildNumber" else "1.0.0-dev"

// Generuje BuildInfo.kt z wersją — odpowiednik androidowego BuildConfig.VERSION_NAME,
// żeby pokazać wersję w ustawieniach aplikacji w runtime.
val generatedBuildInfoDir = layout.buildDirectory.dir("generated/buildinfo/kotlin")
val generateBuildInfo by tasks.registering {
    val outDir = generatedBuildInfoDir
    val versionName = desktopVersionName
    inputs.property("versionName", versionName)
    outputs.dir(outDir)
    doLast {
        val pkg = outDir.get().dir("com/spiewnik/desktop").asFile
        pkg.mkdirs()
        pkg.resolve("BuildInfo.kt").writeText(
            "package com.spiewnik.desktop\n\n" +
                "internal object BuildInfo {\n" +
                "    const val VERSION = \"$versionName\"\n" +
                "}\n"
        )
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(generateBuildInfo)
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
        kotlin.srcDir(generatedBuildInfoDir)
    }
}

compose.desktop {
    application {
        mainClass = "com.spiewnik.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            // Gson tworzy instancje Song przez sun.misc.Unsafe (klasa bez konstruktora
            // bezargumentowego) — Unsafe jest w module jdk.unsupported, który jlink
            // domyślnie pomija w okrojonym JRE. Bez tego zainstalowana apka się wywala.
            modules("jdk.unsupported")
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
