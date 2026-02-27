plugins {
    alias(libs.plugins.kotlinJvm)
    `java-gradle-plugin`
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.publish)
}

gradlePlugin {
    plugins {
        create("jetwhaleHostDevPlugin") {
            id = "com.kitakkun.jetwhale.host.plugin"
            implementationClass = "com.kitakkun.jetwhale.gradle.JetWhaleHostDevGradlePlugin"
        }
    }
}

dependencies {
    compileOnly(libs.kotlinGradlePlugin)
}

jetwhalePublish {
    name = "JetWhale Gradle Plugin"
    description = "A Gradle plugin to assist in developing JetWhale host plugins."
    artifactId = "gradle-plugin"
}
