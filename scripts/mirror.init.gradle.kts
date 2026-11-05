// Puts Google's mirror of Maven Central in place of Maven Central itself (rate-limited here).
val mirror = "https://maven-central.storage-download.googleapis.com/maven2/"
beforeSettings {
    // Added before the settings file's own repositories, so it is asked first for plugins.
    pluginManagement.repositories.maven(mirror) { name = "MavenCentralMirrorPlugins" }
}
settingsEvaluated {
    val repos = dependencyResolutionManagement.repositories
    repos.removeIf { it is MavenArtifactRepository && it.url.toString().contains("repo.maven.apache.org") }
    repos.maven(mirror) { name = "MavenCentralMirror" }
}
