package io.ileukocyte.hibernum.commands.developer

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.commands.TextOnlyCommand
import io.ileukocyte.hibernum.extensions.remove
import io.ileukocyte.hibernum.extensions.sendSuccess
import io.ileukocyte.openweather.Forecast
import io.ileukocyte.openweather.OpenWeatherApi

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.requests.RestAction

import org.json.JSONObject

class EvalCommand : TextOnlyCommand {
    override val name = "eval"
    override val description = "Executes the attached Kotlin code"
    override val aliases = setOf("exec")
    override val usages = setOf("Kotlin code")

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        val code = args?.run {
            takeIf { it.startsWith("```") }
                ?.removeSurrounding("```")
                ?.removePrefix("kt\n")
                ?.removePrefix("kotlin\n")
                ?: this
        } ?: throw NoArgumentsException

        val packages = buildString {
            for ((key, value) in IMPORTS) {
                if (value.isNotEmpty()) {
                    for (`package` in value) {
                        appendLine("import $key.$`package`.*")
                    }
                } else {
                    appendLine("import $key.*")
                }
            }
        }

        try {
            val engine = Immutable.EVAL_KOTLIN_ENGINE

            with(engine.state.history) { if (isNotEmpty()) reset() }

            engine.put("event", event)

            val result: Any? = engine.eval("""
                        |$packages
                        |
                        |$code
                    """.trimMargin())

            if (result !== null) {
                when (result) {
                    is EmbedBuilder -> event.channel.sendMessageEmbeds(result.build()).queue()
                    is Message -> event.channel.sendMessage(result).queue()
                    is MessageEmbed -> event.channel.sendMessageEmbeds(result).queue()
                    is RestAction<*> -> result.queue()
                    is Array<*> -> event.channel.sendMessage(result.contentDeepToString()).queue()
                    is JSONObject -> event.channel.sendMessage(result.toString(2)).queue()
                    is Forecast -> event.channel.sendMessage(result.toString().remove(result.api.key)).queue()
                    is OpenWeatherApi -> event.channel.sendMessage(result.toString().remove(result.key)).queue()
                    else -> event.channel.sendMessage("$result").queue()
                }
            } else event.channel.sendSuccess("Successful execution!").queue()
        } catch (e: Exception) {
            throw CommandException("""
                    |${e::class.simpleName ?: "An unknown exception"} has occurred:
                    |${e.message ?: "No message provided"}
                    |""".trimMargin())
        }
    }

    companion object {
        @JvmField
        val IMPORTS = mutableMapOf(
            "io.ileukocyte" to setOf(
                "hibernum",
                "hibernum.annotations",
                "hibernum.audio",
                "hibernum.builders",
                "hibernum.commands",
                "hibernum.commands.developer",
                "hibernum.commands.`fun`",
                "hibernum.commands.general",
                "hibernum.commands.music",
                "hibernum.commands.utility",
                "hibernum.extensions",
                "hibernum.handlers",
                "hibernum.utils",
                "openweather",
                "openweather.entities",
                "openweather.extensions"
            ),
            "net.dv8tion.jda" to setOf(
                "api",
                "api.audit",
                "api.entities",
                "api.events",
                "api.events.message",
                "api.events.message.guild",
                "api.events.message.react",
                "api.exceptions",
                "api.hooks",
                "api.interactions",
                "api.managers",
                "api.requests",
                "api.requests.restaction",
                "api.requests.restaction.order",
                "api.requests.restaction.pagination",
                "api.utils",
                "api.utils.cache"
            ),
            "io.ktor" to setOf(
                "client",
                "client.engine.cio",
                "client.features",
                "client.request"
            ),
            "java" to setOf(
                "io",
                "lang.management",
                "net",
                "text",
                "time",
                "time.format",
                "time.temporal",
                "util.concurrent"
            ),
            "kotlin" to setOf(
                "concurrent",
                "coroutines",
                "experimental",
                "properties",
                "random",
                "reflect",
                "reflect.full",
                "reflect.jvm",
                "system"
            ),
            "kotlinx" to setOf("coroutines"),
            "org" to setOf("json", "jsoup", "reflections"),
            "mu" to emptySet()
        )
    }
}