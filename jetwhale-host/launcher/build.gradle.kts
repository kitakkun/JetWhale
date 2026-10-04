import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJLinkTask

plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrainsCompose)
}

// The launcher has no composables. The Compose Gradle plugin, applied for desktop packaging, fails
// the build unless the Compose compiler plugin is applied, so it is applied and turned off.
composeCompiler {
    targetKotlinPlatforms.set(emptySet())
}

val bundledHost: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    implementation(projects.jetwhaleHost.releaseMetadata)
    bundledHost(project(path = ":jetwhale-host:app", configuration = "bundledHostElements"))
    testImplementation(libs.kotlinTest)
}

val bundledHostResources = tasks.register<Sync>("prepareBundledHostResources") {
    from(bundledHost) {
        into("common/host")
        // jpackage puts every *.jar under the app directory on the launcher's own class path, so
        // the bundled host jar goes in under another name.
        rename(""".*\.jar""", "jetwhale-host.bundled")
    }
    into(layout.buildDirectory.dir("bundled-host-resources"))
}

val runtimeModulesForLaterHosts = listOf(
    "java.net.http",
    "java.management",
    "jdk.management",
    "jdk.attach",
    "jdk.zipfs",
    "jdk.accessibility",
    "jdk.net",
    "jdk.crypto.cryptoki",
    "jdk.charsets",
    "java.scripting",
    "java.security.jgss",
    "jdk.httpserver",
)

compose.desktop {
    application {
        mainClass = "com.kitakkun.jetwhale.host.launcher.LauncherMainKt"
        nativeDistributions {
            // An installer replaces an earlier install only while the package name, the vendor and
            // the bundle ID stay the same.
            packageName = "JetWhale Debugger"
            copyright = "© 2026 kitakkun"
            // The DMG format takes only a numeric MAJOR.MINOR.PATCH.
            packageVersion = libs.versions.jetwhale.get().substringBefore("-")
            licenseFile = rootProject.rootDir.resolve("LICENSE")
            modules = ArrayList(JetWhaleHostRuntime.modules + runtimeModulesForLaterHosts)
            appResourcesRootDir.set(layout.dir(bundledHostResources.map { it.destinationDir }))

            targetFormats(
                TargetFormat.Dmg,
                TargetFormat.Msi,
                TargetFormat.Deb,
            )

            macOS {
                bundleID = "com.kitakkun.jetwhale.host"
                iconFile.set(rootProject.file("jetwhale-host/app/src/main/resources/icon.icns"))
                // LSUIElement keeps the launcher out of the Dock: it exits once the host has
                // started, and the host shows its own tile.
                infoPlist {
                    extraKeysRawXml = "<key>LSUIElement</key><true/>"
                }
            }
            windows {
                iconFile.set(rootProject.file("jetwhale-host/app/src/main/resources/icon.ico"))
                msiPackageVersion = msiPackageVersion(libs.versions.jetwhale.get())
            }
            linux {
                iconFile.set(rootProject.file("jetwhale-host/app/src/main/resources/icon.png"))
                // Debian sorts `~` below everything, even the end of the string, so 1.0.0~alpha13 <
                // 1.0.0~alpha14 < 1.0.0 and apt upgrades from one pre-release to the next.
                debPackageVersion = libs.versions.jetwhale.get().replace('-', '~')
                menuGroup = "Development;Debugger;"
                appCategory = "devel"
            }
        }
    }
}

tasks.matching { it.name == "prepareAppResources" }.configureEach {
    dependsOn(bundledHostResources)
}

// Compose's jlink step strips the runtime's commands, and the launcher starts hosts with the
// runtime's own java. Compose's signing covers these copies along with the rest of the runtime.
tasks.withType<AbstractJLinkTask>().configureEach {
    val javaHome = javaHome
    val runtimeImage = destinationDir
    doLast {
        val bin = runtimeImage.get().asFile.resolve("bin")
        bin.mkdirs()
        listOf("java", "java.exe", "javaw.exe")
            .map { File(javaHome.get(), "bin/$it") }
            .filter(File::isFile)
            .forEach { executable ->
                val copy = executable.copyTo(bin.resolve(executable.name), overwrite = true)
                copy.setExecutable(true)
            }
    }
}

/**
 * `MAJOR.MINOR.(PATCH × 1000 + S)`, where S is 100 + N for alphaN, 300 + N for betaN, 500 + N for rcN
 * and 900 for a final release. Windows Installer treats two MSIs of the same version as one product,
 * so every release needs its own, and this one also orders them. It fits MSI's 255.255.65535 up to
 * patch 64.
 */
fun msiPackageVersion(version: String): String {
    val match = checkNotNull(Regex("""(\d+)\.(\d+)\.(\d+)(?:-(alpha|beta|rc)(\d+))?""").matchEntire(version)) {
        "$version is not a release version, so it has no MSI version"
    }
    val (major, minor, patch, stage, number) = match.destructured
    val stageOffset = when (stage) {
        "alpha" -> 100
        "beta" -> 300
        "rc" -> 500
        else -> 900
    }
    val stageNumber = if (stage.isEmpty()) 0 else number.toInt()
    check(stage.isEmpty() || stageNumber in 1..199) { "$version has a pre-release number outside 1..199" }
    val build = patch.toInt() * 1000 + stageOffset + stageNumber
    check(major.toInt() <= 255 && minor.toInt() <= 255 && build <= 65535) { "$version does not fit an MSI version" }
    return "$major.$minor.$build"
}
