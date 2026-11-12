plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.google.services) apply false
}
// Local Firebase/OAuth configuration is never committed and is optional for offline/test builds.
if (file("google-services.json").isFile) apply(plugin = "com.google.gms.google-services")
android {
    namespace = "lvluplife"
    compileSdk = 37
    defaultConfig {
        applicationId = "lvluplife.android"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        val apiBaseUrl = providers.gradleProperty("lvlup.apiBaseUrl").orElse("").get()
        buildConfigField("String", "API_BASE_URL", "\"" + apiBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["notAnnotation"] = "lvluplife.data.ProcessStage"
    }
    buildFeatures { compose = true; buildConfig = true }
    buildTypes {
        getByName("debug") {
            if (!providers.gradleProperty("lvlup.apiBaseUrl").isPresent)
                buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8081\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }
room { schemaDirectory("$projectDir/schemas") }
dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.room.runtime)
    implementation(libs.okhttp)
    implementation(libs.jackson.databind)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.play.services)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.firebase.auth.android)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    // Espresso 3.7 uses getSystemService instead of the API removed in Android 17.
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
}

tasks.withType<Test>().configureEach {
    // Security tests inspect actual merged manifests and overlaid resources of both variants.
    dependsOn("processDebugManifest", "processReleaseManifest", "packageDebugResources", "packageReleaseResources", "generateReleaseBuildConfig")
}
