import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kotlinVersion: String by project
val ktorVersion: String by project

plugins {
    java

    kotlin("jvm") version "1.7.20-Beta"
    kotlin("plugin.serialization") version "1.7.20-Beta"

    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("com.github.ben-manes.versions") version "0.42.0"
}

group = "io.ileukocyte"
version = Version(major = 3, minor = 9, patch = 1)

@Suppress("DEPRECATION")
repositories {
    mavenCentral()

    maven {
        name = "m2-dv8tion"
        url = uri("https://m2.dv8tion.net/releases")
    }

    jcenter()

    maven {
        name = "jitpack"
        url = uri("https://jitpack.io")
    }
}

dependencies {
    // Discord
    implementation(group = "com.github.DV8FromtheWorld", name = "JDA", version = "ae85f75470") {
        exclude(module = "opus-java")
    }
    //implementation(group = "com.sedmelluq", name = "lavaplayer", version = "1.3.78")
    implementation(group = "com.github.walkyst", name = "lavaplayer-fork", version = "1.3.98.4")
    implementation(group = "com.github.natanbc", name = "lavadsp", version = "0.7.7")

    // Logging
    implementation(group = "ch.qos.logback", name = "logback-classic", version = "1.2.11")
    implementation(group = "io.github.microutils", name = "kotlin-logging-jvm", version = "2.1.23")

    // APIs and libraries
    implementation(group = "com.github.markozajc", name = "akiwrapper", version = "1.5.2")
    implementation(group = "com.github.ileukocyte", name = "openweather-kt", version = "1.1.1")
    implementation(group = "org.reflections", name = "reflections", version = "0.10.2")
    implementation(group = "org.json", name = "json", version = "20220320")
    implementation(group = "com.google.api-client", name = "google-api-client", version = "2.0.0")
    implementation(group = "com.google.oauth-client", name = "google-oauth-client-jetty", version = "1.34.1")
    implementation(group = "com.google.apis", name = "google-api-services-youtube", version = "v3-rev20220719-2.0.0")
    implementation(group = "com.google.guava", name = "guava", version = "31.1-jre")
    implementation(group = "org.jsoup", name = "jsoup", version = "1.15.3")
    implementation(group = "commons-validator", name = "commons-validator", version = "1.7")
    implementation(group = "com.github.kenglxn.QRGen", name = "javase", version = "2.6.0")
    implementation(group = "se.michaelthelin.spotify", name = "spotify-web-api-java", version = "7.2.0")
    implementation(group = "com.github.SvenWoltmann", name = "color-thief-java", version = "v1.1.2")
    implementation(group = "org.ocpsoft.prettytime", name = "prettytime", version = "5.0.4.Final")
    implementation(group = "io.arrow-kt", name = "arrow-fx-coroutines", version = "1.1.2")
    implementation(group = "org.sejda.imageio", name = "webp-imageio", version = "0.1.6")
    implementation(group = "ca.pjer", name = "chatter-bot-api", version = "2.0.1")

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
    implementation(kotlinx("coroutines-core", "1.6.4"))
    implementation(kotlinx("serialization-json", "1.4.0-RC"))
    implementation(kotlinx("datetime", "0.4.0"))

    // Ktor
    implementation(ktor("client-content-negotiation"))
    implementation(ktor("client-core"))
    implementation(ktor("client-okhttp"))
    implementation(ktor("serialization-kotlinx-json"))

    // Testing
    testImplementation(kotlin("test-junit", kotlinVersion))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.0")
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
repositories {
    mavenCentral()
}

tasks.withType<ShadowJar> {
    archiveBaseName.set("hibernum")
    archiveClassifier.set("")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
    //kotlinOptions.freeCompilerArgs += setOf("-Xuse-k2", "-XXLanguage:+RangeUntilOperator")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

data class Version(
    val major: Int,
    val minor: Int,
    val patch: Int = 0,
    val stability: Stability = Stability.STABLE,
    val unstable: Int = 0,
) {
    override fun toString() = arrayOf(
        major,
        minor,
        patch.takeUnless { it == 0 },
    ).filterNotNull().joinToString(separator = ".") + stability.toString()
        .takeUnless { stability == Stability.STABLE }
        ?.let { "-$it${unstable.takeIf { u -> u != 0 } ?: ""}" }
        .orEmpty()

    enum class Stability(private val suffix: String? = null) {
        STABLE,
        RELEASE_CANDIDATE("RC"),
        BETA,
        ALPHA;

        override fun toString() = suffix ?: name
    }
}