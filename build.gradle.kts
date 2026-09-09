plugins {
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}

// AGP 9.x ships built-in Kotlin. Pin the Kotlin Gradle runtime so the Compose
// compiler plugin and Kotlin compiler stay aligned.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}
