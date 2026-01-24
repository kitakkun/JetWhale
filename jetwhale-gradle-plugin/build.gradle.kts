plugins {
    alias(libs.plugins.kotlinJvm)
    `java-gradle-plugin`
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
