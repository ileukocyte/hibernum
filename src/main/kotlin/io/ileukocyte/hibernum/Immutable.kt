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

    val VERSION = Version(major = 1, minor = 0, stability = Version.Stability.Beta, unstable = 2)

    val USER_AGENT = "User-Agent: DiscordBot ($GITHUB_REPOSITORY, $VERSION)"

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
    ).filterNotNull().joinToString(separator = ".") + stability.suffix?.let { "-$it$unstable" }.orEmpty()

    sealed class Stability(val suffix: String? = null) {
        object Stable : Stability()
        object ReleaseCandidate : Stability("RC")
        object Beta : Stability("BETA")
        object Alpha : Stability("ALPHA")

        override fun toString() = suffix ?: "STABLE"
    }
}