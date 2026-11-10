buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Override AGP's built-in Kotlin without applying kotlin.android.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}

allprojects {
    dependencyLocking { lockAllConfigurations() }
}

tasks.register("reportBuildVersions") {
    group = "verification"
    description = "Reports resolved build plugins; Android compilation is validated separately."
    doLast {
        val artifacts = buildscript.configurations.getByName("classpath")
            .resolvedConfiguration.resolvedArtifacts
        val coordinates = listOf(
            "AGP" to "com.android.tools.build:gradle",
            "KGP" to "org.jetbrains.kotlin:kotlin-gradle-plugin",
            "Compose compiler" to "org.jetbrains.kotlin:compose-compiler-gradle-plugin",
            "KSP" to "com.google.devtools.ksp:symbol-processing-gradle-plugin",
        )
        coordinates.forEach { (label, coordinate) ->
            val artifact = artifacts.single { "${it.moduleVersion.id.group}:${it.name}" == coordinate }
            println("$label=${artifact.moduleVersion.id.version}")
        }
        println("Gradle=${gradle.gradleVersion}")
        println("JVM=${System.getProperty("java.version")}")
    }
}
