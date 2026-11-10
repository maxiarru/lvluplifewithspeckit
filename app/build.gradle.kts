plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}
android {
    namespace = "lvluplife"
    compileSdk = 37
    defaultConfig {
        applicationId = "lvluplife.android"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["notAnnotation"] = "lvluplife.data.ProcessStage"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}

tasks.withType<Test>().configureEach {
    // Security tests inspect actual merged manifests and overlaid resources of both variants.
    dependsOn("processDebugManifest", "processReleaseManifest", "packageDebugResources", "packageReleaseResources")
}
