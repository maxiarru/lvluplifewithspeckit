plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}
kotlin { jvmToolchain(17) }
application { mainClass.set("lvluplife.api.ServerKt") }
val integrationTest by sourceSets.creating
configurations[integrationTest.implementationConfigurationName]
    .extendsFrom(configurations.testImplementation.get())
configurations[integrationTest.runtimeOnlyConfigurationName]
    .extendsFrom(configurations.testRuntimeOnly.get())
dependencies {
    implementation(project(":domain"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.firebase.admin)
    implementation(libs.google.firestore)
    implementation(libs.jackson.databind)
    implementation(libs.ktor.server.status.pages)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.ktor.server.test.host)
    add(integrationTest.implementationConfigurationName, sourceSets.main.get().output)
}
tasks.test { useJUnit() }
val integrationTestTask = tasks.register<Test>("integrationTest") {
    description = "Runs integration tests against isolated local emulators."
    group = "verification"
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    useJUnit()
    shouldRunAfter(tasks.test)
    // Test-only child JVM for cold startup; never added to the production runtime.
    val performanceClasspath = layout.buildDirectory.file("tmp/performance-runtime-classpath.txt")
    systemProperty("lvlup.perf.classpathFile", performanceClasspath.get().asFile.absolutePath)
    doFirst {
        performanceClasspath.get().asFile.apply {
            parentFile.mkdirs()
            writeText(integrationTest.runtimeClasspath.asPath)
        }
    }
    systemProperty("lvlup.perf.ktorVersion", libs.versions.ktor.get())
    systemProperty("lvlup.perf.firestoreVersion", libs.versions.firestore.get())
    for (key in listOf("LVLUP_PERF_PROFILE", "LVLUP_PERF_N", "LVLUP_PERF_WARMUP",
        "LVLUP_PERF_WORKERS", "LVLUP_PERF_SEED", "LVLUP_PERF_COLD_N", "LVLUP_PERF_REVISION")) {
        inputs.property("performance.$key", providers.environmentVariable(key).orElse("DEFAULT"))
    }
    outputs.dir(layout.buildDirectory.dir("reports/performance"))
}
tasks.check { dependsOn(integrationTestTask) }
