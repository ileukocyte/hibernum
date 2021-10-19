import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kotlinVersion: String by project
val ktorVersion: String by project

plugins {
    java

    kotlin("jvm") version "1.5.31"
    kotlin("plugin.serialization") version "1.5.31"

    id("com.github.johnrengelman.shadow") version "7.1.0"
    id("com.github.ben-manes.versions") version "0.39.0"
}

group = "io.ileukocyte"
version = Version(major = 2, minor = 1)

repositories {
    mavenCentral()

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
    implementation(group = "net.dv8tion", name = "JDA", version = "4.3.0_334") { exclude(module = "opus-java") }
    implementation(group = "com.sedmelluq", name = "lavaplayer", version = "1.3.78")

    // Logging
    implementation(group = "ch.qos.logback", name = "logback-classic", version = "1.2.6")
    implementation(group = "io.github.microutils", name = "kotlin-logging-jvm", version = "2.0.11")

    // APIs and libraries
    implementation(group = "com.github.markozajc", name = "akiwrapper", version = "1.5.1.1")
    implementation(group = "com.github.ileukocyte", name = "openweather-kt", version = "dfaea810a8")
    implementation(group = "org.reflections", name = "reflections", version = "0.10.1")
    implementation(group = "org.json", name = "json", version = "20210307")
    implementation(group = "com.google.api-client", name = "google-api-client", version = "1.32.1")
    implementation(group = "com.google.oauth-client", name = "google-oauth-client-jetty", version = "1.32.1")
    implementation(group = "com.google.apis", name = "google-api-services-youtube", version = "v3-rev20210915-1.32.1")
    implementation(group = "com.google.guava", name = "guava", version = "31.0.1-jre")
    implementation(group = "org.jsoup", name = "jsoup", version = "1.14.3")
    implementation(group = "commons-validator", name = "commons-validator", version = "1.7")
    implementation(group = "com.github.kenglxn.QRGen", name = "javase", version = "2.6.0")
    implementation(group = "se.michaelthelin.spotify", name = "spotify-web-api-java", version = "6.5.4")
    implementation(group = "com.github.SvenWoltmann", name = "color-thief-java", version = "v1.1.2")
    implementation(group = "org.ocpsoft.prettytime", name = "prettytime", version = "5.0.2.Final")
    implementation(group = "io.arrow-kt", name = "arrow-fx-coroutines", version = "1.0.0")

    // Kotlin
    implementation(kotlin("stdlib", kotlinVersion))
    implementation(kotlin("reflect", kotlinVersion))
    implementation(kotlin("compiler", kotlinVersion))
    implementation(kotlin("script-util", kotlinVersion))
    implementation(kotlin("scripting-compiler", kotlinVersion))
    implementation(kotlin("scripting-jsr223", kotlinVersion))
    implementation(kotlin("scripting-jvm-host", kotlinVersion))
    implementation(kotlinx("coroutines-core", "1.5.2"))
    implementation(kotlinx("serialization-json", "1.3.0"))
    implementation(kotlinx("datetime", "0.3.0"))

    // Ktor
    implementation(ktor("client-core"))
    implementation(ktor("client-cio"))
    implementation(ktor("client-serialization"))

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testImplementation(kotlin("test-junit", kotlinVersion))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
}

fun kotlinx(module: String, version: String) = "org.jetbrains.kotlinx:kotlinx-$module:$version"

fun ktor(module: String, version: String = ktorVersion) = "io.ktor:ktor-$module:$version"

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
    val unstable: Int = 0,
) {
    override fun toString() = arrayOf(
        major,
        minor,
        patch.takeUnless { it == 0 },
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