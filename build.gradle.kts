// Plugins are declared per-module (see core/ and app/) so that the pure-JVM
// :core module can be built without resolving the Android Gradle Plugin.
tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
