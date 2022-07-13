import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kotlinVersion: String by project
val ktorVersion: String by project

plugins {
    java

    kotlin("jvm") version "1.7.10"
    kotlin("plugin.serialization") version "1.7.10"

    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("com.github.ben-manes.versions") version "0.42.0"
}

group = "io.ileukocyte"
version = Version(major = 3, minor = 0, unstable = 1, stability = Version.Stability.Alpha)

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
    implementation(group = "net.dv8tion", name = "JDA", version = "5.0.0-alpha.13") { exclude(module = "opus-java") }
    implementation(group = "com.sedmelluq", name = "lavaplayer", version = "1.3.78")

    // Logging
    implementation(group = "ch.qos.logback", name = "logback-classic", version = "1.3.0-alpha16")
    implementation(group = "io.github.microutils", name = "kotlin-logging-jvm", version = "2.1.23")

    // APIs and libraries
    implementation(group = "com.github.markozajc", name = "akiwrapper", version = "1.5.2")
    implementation(group = "com.github.ileukocyte", name = "openweather-kt", version = "1.1.1")
    implementation(group = "org.reflections", name = "reflections", version = "0.10.2")
    implementation(group = "org.json", name = "json", version = "20220320")
    implementation(group = "com.google.api-client", name = "google-api-client", version = "1.35.1")
    implementation(group = "com.google.oauth-client", name = "google-oauth-client-jetty", version = "1.34.1")
    implementation(group = "com.google.apis", name = "google-api-services-youtube", version = "v3-rev20220612-1.32.1")
    implementation(group = "com.google.guava", name = "guava", version = "31.1-jre")
    implementation(group = "org.jsoup", name = "jsoup", version = "1.14.3")
    implementation(group = "commons-validator", name = "commons-validator", version = "1.7")
    implementation(group = "com.github.kenglxn.QRGen", name = "javase", version = "2.6.0")
    implementation(group = "se.michaelthelin.spotify", name = "spotify-web-api-java", version = "7.1.0")
    implementation(group = "com.github.SvenWoltmann", name = "color-thief-java", version = "v1.1.2")
    implementation(group = "org.ocpsoft.prettytime", name = "prettytime", version = "5.0.3.Final")
    implementation(group = "io.arrow-kt", name = "arrow-fx-coroutines", version = "1.1.2")
    implementation(group = "org.sejda.imageio", name = "webp-imageio", version = "0.1.6")

    // Kotlin
    implementation(kotlin("stdlib", kotlinVersion))
    implementation(kotlin("stdlib-jdk7", kotlinVersion))
    implementation(kotlin("stdlib-jdk8", kotlinVersion))
    implementation(kotlin("reflect", kotlinVersion))
    implementation(kotlin("compiler", kotlinVersion))
    implementation(kotlin("script-util", kotlinVersion))
    implementation(kotlin("scripting-compiler", kotlinVersion))
    implementation(kotlin("scripting-jsr223", kotlinVersion))
    implementation(kotlin("scripting-jvm-host", kotlinVersion))
    implementation(kotlinx("coroutines-core", "1.6.3"))
    implementation(kotlinx("serialization-json", "1.3.3"))
    implementation(kotlinx("datetime", "0.4.0"))

    // Ktor
    implementation(ktor("client-content-negotiation"))
    implementation(ktor("client-core"))
    implementation(ktor("client-cio"))
    implementation(ktor("serialization-kotlinx-json"))

    // Testing
    testImplementation(kotlin("test-junit", kotlinVersion))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.2")
}

fun kotlinx(module: String, version: String) = "org.jetbrains.kotlinx:kotlinx-$module:$version"

fun ktor(module: String, version: String = ktorVersion) = "io.ktor:ktor-$module:$version"

configurations {
    all {
        resolutionStrategy.sortArtifacts(ResolutionStrategy.SortOrder.DEPENDENCY_FIRST)
    }
}

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
    kotlinOptions.jvmTarget = "17"
    //kotlinOptions.freeCompilerArgs += setOf("-Xuse-k2")
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