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
}
tasks.check { dependsOn(integrationTestTask) }
