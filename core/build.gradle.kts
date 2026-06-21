plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure-Kotlin/JVM module shared by :androidApp and :desktopApp.
// MUST NOT depend on android.* — only standard Kotlin/Java + multiplatform-safe libs.

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(libs.gson)
    testImplementation(libs.junit)
}
