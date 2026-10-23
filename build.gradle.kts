buildscript {
    dependencies {
        // AGP 9 compiles Kotlin itself (built-in Kotlin) with this Kotlin Gradle plugin version.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
