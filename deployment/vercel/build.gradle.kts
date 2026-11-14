plugins { alias(libs.plugins.kotlin.jvm) apply false }

allprojects {
    dependencyLocking { lockAllConfigurations() }
}
