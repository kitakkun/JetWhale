plugins {
    kotlin("jvm")
    application
}

val jetwhaleVersion = providers.gradleProperty("jetwhaleVersion").orNull
    ?: error("Missing -PjetwhaleVersion=<version>, e.g. -PjetwhaleVersion=1.0.0-alpha07")

kotlin {
    // Pin the toolchain to the repo's convention (gradle-conventions/src/main/kotlin/jvm.gradle.kts)
    // so matrix failures can only come from Kotlin compatibility, not local JDK differences.
    jvmToolchain(17)
}

if (providers.gradleProperty("skipMetadataCheck").isPresent) {
    kotlin.compilerOptions.freeCompilerArgs.add("-Xskip-metadata-version-check")
}

dependencies {
    implementation("com.kitakkun.jetwhale:jetwhale-agent-runtime:$jetwhaleVersion")
}

application {
    mainClass.set("MainKt")
}
