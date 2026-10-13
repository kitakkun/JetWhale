allprojects {
    tasks.withType<org.gradle.plugins.signing.Sign>().configureEach { enabled = false }
}
