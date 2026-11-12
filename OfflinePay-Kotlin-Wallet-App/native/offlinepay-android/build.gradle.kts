buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Override AGP's bundled Kotlin version so the compiler can parse JDK 26.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    id("com.android.application") version "9.3.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
