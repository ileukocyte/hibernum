package io.ileukocyte.hibernum

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

import java.awt.Color

import javax.script.ScriptEngineManager

import kotlin.script.experimental.jvmhost.jsr223.KotlinJsr223ScriptEngineImpl

import mu.KotlinLogging

object Immutable {
    @JvmField
    val DISCORD_TOKEN: String = System.getenv("DISCORD_TOKEN")

    const val DEFAULT_PREFIX = "&"
    const val GITHUB_REPOSITORY = "https://github.com/ileukocyte/hibernum"
    const val INVITE_LINK_FORMAT = "https://discord.com/api/oauth2/authorize?client_id=%s" +
            "&permissions=%d" +
            "&scope=applications.commands%%20bot"

    @JvmField
    val LOGGER = KotlinLogging.logger("Hibernum")

    @JvmField
    val HTTP_CLIENT = HttpClient(OkHttp)

    @JvmField
    val EVAL_KOTLIN_ENGINE = ScriptEngineManager().getEngineByExtension("kts")
            as KotlinJsr223ScriptEngineImpl
    @JvmField
    val EVAL_MODAL_INPUT_BACKUP_CHANNEL_ID: String? =
        System.getenv("EVAL_MODAL_INPUT_BACKUP_CHANNEL_ID")

    @JvmField
    val VERSION = Version(major = 3, minor = 6)

    @JvmField
    val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:90.0) Gecko/20100101 Firefox/90.0" //"User-Agent: DiscordBot ($GITHUB_REPOSITORY, $VERSION)"
    @JvmField
    val YOUTUBE_API_KEY: String = System.getenv("YOUTUBE_API_KEY")
    @JvmField
    val PERSPECTIVE_API_KEY: String = System.getenv("PERSPECTIVE_API_KEY")
    @JvmField
    val WEATHER_API_KEY: String = System.getenv("WEATHER_API_KEY")
    @JvmField
    val SPOTIFY_CLIENT_ID: String = System.getenv("SPOTIFY_CLIENT_ID")
    @JvmField
    val SPOTIFY_CLIENT_SECRET: String = System.getenv("SPOTIFY_CLIENT_SECRET")

    @JvmField
    val SUCCESS = Color(140, 190, 218)
    @JvmField
    val FAILURE = Color(239, 67, 63)
    @JvmField
    val CONFIRMATION = Color(118, 255, 3)
    @JvmField
    val WARNING = Color(255, 242, 54)

    @JvmField
    val DEVELOPERS = mutableSetOf<Long>()
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