package io.ileukocyte.hibernum

import mu.KotlinLogging

import java.awt.Color
import javax.script.ScriptEngineManager

import kotlin.script.experimental.jvmhost.jsr223.KotlinJsr223ScriptEngineImpl

object Immutable {
    val DISCORD_TOKEN: String = System.getenv("DISCORD_TOKEN")

    const val DEFAULT_PREFIX = "&"
    const val GITHUB_REPOSITORY = "https://github.com/ileukocyte/hibernum"
    const val INVITE_LINK_FORMAT = "https://discord.com/api/oauth2/authorize?client_id=%s" +
            "&permissions=4294967287" +
            "&scope=applications.commands%%20bot"

    val LOGGER = KotlinLogging.logger("Hibernum")

    val EVAL_KOTLIN_ENGINE = ScriptEngineManager().getEngineByExtension("kts") as KotlinJsr223ScriptEngineImpl

    val VERSION = Version(major = 1, minor = 0, patch = 4)

    val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:90.0) Gecko/20100101 Firefox/90.0" //"User-Agent: DiscordBot ($GITHUB_REPOSITORY, $VERSION)"
    val YOUTUBE_API_KEY: String = System.getenv("YOUTUBE_API_KEY")
    val PERSPECTIVE_API_KEY: String = System.getenv("PERSPECTIVE_API_KEY")
    val WEATHER_API_KEY: String = System.getenv("WEATHER_API_KEY")

    val SUCCESS = Color(140, 190, 218)
    val FAILURE = Color(239, 67, 63)
    val CONFIRMATION = Color(118, 255, 3)
    val WARNING = Color(255, 242, 54)

    val DEVELOPERS = mutableSetOf<Long>()
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