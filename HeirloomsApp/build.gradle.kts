plugins {
    id("com.android.application") version "8.8.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    // Kotlin 2.0+ bundles the Compose compiler; apply this plugin instead of
    // kotlinCompilerExtensionVersion in composeOptions.
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
