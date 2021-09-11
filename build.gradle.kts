import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kotlinVersion: String by project
val coroutinesVersion: String by project
val jdaVersion: String by project
val ktorVersion: String by project

plugins {
    java

    kotlin("jvm") version "1.5.30"

    id("com.github.johnrengelman.shadow") version "5.1.0"
    id("com.github.ben-manes.versions") version "0.19.0"
}

group = "io.ileukocyte"
version = Version(major = 1, minor = 6, unstable = 4, stability = Version.Stability.Beta)

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
    implementation(group = "ch.qos.logback", name = "logback-classic", version = "1.2.5")
    implementation(group = "io.github.microutils", name = "kotlin-logging-jvm", version = "2.0.11")

    // APIs and libraries
    implementation(group = "com.github.markozajc", name = "akiwrapper", version = "1.5.1.1")
    implementation(group = "com.github.ileukocyte", name = "openweather-kt", version = "1.0")
    implementation(group = "org.reflections", name = "reflections", version = "0.9.12")
    implementation(group = "org.json", name = "json", version = "20210307")
    implementation(group = "com.google.api-client", name = "google-api-client", version = "1.32.1")
    implementation(group = "com.google.oauth-client", name = "google-oauth-client-jetty", version = "1.32.1")
    implementation(group = "com.google.apis", name = "google-api-services-youtube", version = "v3-rev20210828-1.32.1")
    implementation(group = "com.google.guava", name = "guava", version = "30.1.1-jre")
    implementation(group = "io.ktor", name = "ktor-client-core", version = ktorVersion)
    implementation(group = "io.ktor", name = "ktor-client-cio", version = ktorVersion)
    implementation(group = "org.jsoup", name = "jsoup", version = "1.14.2")
    implementation(group = "commons-validator", name = "commons-validator", version = "1.7")
    implementation(group = "com.github.kenglxn.QRGen", name = "javase", version = "2.6.0")
    implementation(group = "se.michaelthelin.spotify", name = "spotify-web-api-java", version = "6.5.4")
    implementation(group = "com.github.SvenWoltmann", name = "color-thief-java", version = "v1.1.2")

    // Kotlin
    implementation(kotlin("stdlib", kotlinVersion))
    implementation(kotlin("reflect", kotlinVersion))
    implementation(kotlin("compiler", kotlinVersion))
    implementation(kotlin("script-util", kotlinVersion))
    implementation(kotlin("scripting-compiler", kotlinVersion))
    implementation(kotlin("scripting-jsr223", kotlinVersion))
    implementation(kotlin("scripting-jvm-host", kotlinVersion))
    implementation(kotlinx("coroutines-core", coroutinesVersion))

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.2")
    testImplementation(kotlin("test-junit"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.2")
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
    kotlinOptions.freeCompilerArgs += setOf("-Xopt-in=kotlin.RequiresOptIn", "-Xunrestricted-builder-inference")
    //kotlinOptions.languageVersion = "1.6"
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
    ).filterNotNull().joinToString(separator = ".") +
            stability.suffix?.let { "-$it${unstable.takeIf { u -> u != 0 } ?: ""}" }.orEmpty()

    sealed class Stability(val suffix: String? = null) {
        object Stable : Stability()
        object ReleaseCandidate : Stability("RC")
        object Beta : Stability("BETA")
        object Alpha : Stability("ALPHA")

        override fun toString() = suffix ?: "STABLE"
    }
}