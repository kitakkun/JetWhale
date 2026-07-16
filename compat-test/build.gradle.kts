plugins {
    kotlin("jvm")
    application
}

val jetwhaleVersion = providers.gradleProperty("jetwhaleVersion").get()

if (providers.gradleProperty("skipMetadataCheck").isPresent) {
    kotlin.compilerOptions.freeCompilerArgs.add("-Xskip-metadata-version-check")
}

dependencies {
    implementation("com.kitakkun.jetwhale:jetwhale-agent-runtime:$jetwhaleVersion")
}

application {
    mainClass.set("MainKt")
}
