// Lets `publishToMavenLocal` run without the release signing key, for checks on what would be published.
allprojects {
    tasks.withType<org.gradle.plugins.signing.Sign>().configureEach { enabled = false }
}
