import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kotlinVersion: String by project
val coroutinesVersion: String by project
val jdaVersion: String by project

plugins {
    java
    kotlin("jvm") version "1.5.21"
    id("com.github.johnrengelman.shadow") version "5.1.0"
    id("com.github.ben-manes.versions") version "0.19.0"
}

group = "io.ileukocyte"
version = Version(major = 1, minor = 0, stability = Version.Stability.Beta, unstable = 4)

repositories {
    mavenCentral()

    jcenter()

    maven {
        name = "m2-dv8tion"
        url = uri("https://m2.dv8tion.net/releases")
    }

    maven {
        name = "jitpack"
        url = uri("https://jitpack.io")
    }
}

dependencies {
    // Discord
    implementation(group = "net.dv8tion", name = "JDA", version = jdaVersion) { exclude(module = "opus-java") }
    implementation(group = "com.sedmelluq", name = "lavaplayer", version = "1.3.78")

    // Logging
    implementation(group = "ch.qos.logback", name = "logback-classic", version = "1.2.3")
    implementation(group = "io.github.microutils", name = "kotlin-logging-jvm", version = "2.0.8")

    // APIs
    implementation(group = "com.github.markozajc", name = "akiwrapper", version = "1.5.1.1")
    implementation(group = "org.reflections", name = "reflections", version = "0.9.12")
    implementation(group = "org.json", name = "json", version = "20210307")
    implementation(group = "com.google.api-client", name = "google-api-client", version = "1.23.0")
    implementation(group = "com.google.oauth-client", name = "google-oauth-client-jetty", version = "1.23.0")
    implementation(group = "com.google.apis", name = "google-api-services-youtube", version = "v3-rev222-1.25.0")

    // Kotlin
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation(kotlin("compiler"))
    implementation(kotlin("script-util"))
    implementation(kotlin("scripting-compiler"))
    implementation(kotlin("scripting-jsr223"))
    implementation(kotlin("scripting-jvm-host"))
    implementation(kotlinx("coroutines-core", version = coroutinesVersion))

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testImplementation(kotlin("test-junit"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

fun kotlinx(module: String, version: String) = "org.jetbrains.kotlinx:kotlinx-$module:$version"

val build: DefaultTask by tasks
val clean: Delete by tasks
val jar: Jar by tasks
val shadowJar: ShadowJar by tasks

build.apply {
    dependsOn(clean)
    dependsOn(shadowJar)

    jar.mustRunAfter(clean)
}

tasks.withType<ShadowJar> {
    archiveBaseName.set("hibernum")
    archiveClassifier.set("")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

data class Version(
    val major: Int,
    val minor: Int,
    val patch: Int = 0,
    val stability: Stability = Stability.Stable,
    val unstable: Int = 0
) {
    override fun toString() = arrayOf(
        major,
        minor,
        patch.takeUnless { it == 0 }
    ).filterNotNull().joinToString(separator = ".") + stability.suffix?.let { "-$it$unstable" }.orEmpty()

    sealed class Stability(val suffix: String? = null) {
        object Stable : Stability()
        object ReleaseCandidate : Stability("RC")
        object Beta : Stability("BETA")
        object Alpha : Stability("ALPHA")

        override fun toString() = suffix ?: "STABLE"
    }
}