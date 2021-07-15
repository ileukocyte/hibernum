package io.ileukocyte.hibernum

import mu.KotlinLogging

import java.awt.Color
import javax.script.ScriptEngineManager

@Suppress("UNUSED")
object Immutable {
    const val DEFAULT_PREFIX = "&"

    val DISCORD_TOKEN: String = System.getenv("DISCORD_TOKEN")
    val LOGGER = KotlinLogging.logger("Hibernum")

    val EVAL_KOTLIN_ENGINE = ScriptEngineManager().getEngineByExtension("kts")
        ?: throw UninitializedPropertyAccessException()

    val VERSION = Version(major = 1, minor = 0, stability = Version.Stability.Alpha, unstable = 12)

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
    ).filterNotNull().joinToString(separator = ".") + stability.run {
        if (suffix !== null) {
            "-$suffix$unstable"
        } else ""
    }

    sealed class Stability(val suffix: String? = null) {
        object Stable : Stability()
        object ReleaseCandidate : Stability("RC")
        object Beta : Stability("BETA")
        object Alpha : Stability("ALPHA")

        override fun toString() = suffix ?: "STABLE"
    }
}