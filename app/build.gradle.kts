plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    alias(libs.plugins.kotlin.jvm)

    // Apply the application plugin to add support for building a CLI application in Java.
    application
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    implementation("com.github.ajalt.clikt:clikt:5.0.3")
    implementation("com.github.ajalt.mordant:mordant:3.0.2")
    implementation("com.github.ajalt.mordant:mordant-coroutines:3.0.2")
    // implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}

// Apply a specific Java toolchain to ease working on different environments.
java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }

application {
    // Define the main class for the application.
    mainClass = "com.gitlab.notscripter.composecli.MainKt"

    tasks.register<Copy>("copyTemplates") {
        from("build/libs/templates")
        into("build/install/app/lib/templates")
        doLast { println("✅ Templates copied to install path.") }
    }
}
