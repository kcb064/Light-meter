// Plugins are declared per-module (see core/ and app/) so that the pure-JVM
// :core module can be built without resolving the Android Gradle Plugin
// (see LIGHTMETER_CORE_ONLY in settings.gradle.kts). This makes Gradle warn
// that the Kotlin plugin is "loaded multiple times"; that warning is the cost
// of the split — loading Kotlin once at the root breaks the kotlin-android
// plugin, which must share a classloader with AGP.
tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
