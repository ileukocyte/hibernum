package io.ileukocyte.hibernum.commands.developer

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.extensions.remove
import io.ileukocyte.hibernum.extensions.replySuccess
import io.ileukocyte.hibernum.extensions.sendSuccess
import io.ileukocyte.openweather.Forecast
import io.ileukocyte.openweather.OpenWeatherApi

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.components.Modal
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.requests.RestAction

import org.json.JSONObject

class EvalCommand : Command {
    override val name = "eval"
    override val description = "Executes the attached Kotlin code"
    override val aliases = setOf("exec")
    override val usages = setOf(setOf("Kotlin code"))

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
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

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val input = TextInput
            .create("$name-code", "Enter Your Kotlin Code:", TextInputStyle.PARAGRAPH)
            .build()
        val modal = Modal
            .create("$name-${event.user.idLong}", "Kotlin Code Execution")
            .addActionRow(input)
            .build()

        event.replyModal(modal).queue()
    }

    override suspend fun invoke(event: ModalInteractionEvent) {
        val id = event.modalId.removePrefix("$name-")

        if (event.user.id == id) {
            val code = event.getValue("$name-code")?.asString ?: return

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
                        is EmbedBuilder -> event.replyEmbeds(result.build()).queue()
                        is Message -> event.reply(result).queue()
                        is MessageEmbed -> event.replyEmbeds(result).queue()
                        is RestAction<*> -> {
                            event.replySuccess("Successful execution!").setEphemeral(true).queue()

                            result.queue()
                        }
                        is Array<*> -> event.reply(result.contentDeepToString()).queue()
                        is JSONObject -> event.reply(result.toString(2)).queue()
                        is Forecast -> event.reply(result.toString().remove(result.api.key)).queue()
                        is OpenWeatherApi -> event.reply(result.toString().remove(result.key)).queue()
                        else -> event.reply("$result").queue()
                    }
                } else {
                    event.replySuccess("Successful execution!").setEphemeral(true).queue()
                }
            } catch (e: Exception) {
                throw CommandException("""
                    |${e::class.simpleName ?: "An unknown exception"} has occurred:
                    |${e.message ?: "No message provided"}
                    |""".trimMargin())
            }
        }
    }

    companion object {
        @JvmField
        val IMPORTS = mutableMapOf(
            "io.ileukocyte" to setOf(
                "hibernum",
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
                "openweather.extensions",
            ),
            "net.dv8tion.jda" to setOf(
                "api",
                "api.audit",
                "api.entities",
                "api.events",
                "api.events.guild",
                "api.events.guild.voice",
                "api.events.interaction",
                "api.events.interaction.command",
                "api.events.interaction.component",
                "api.events.message",
                "api.events.message",
                "api.events.message.react",
                "api.exceptions",
                "api.hooks",
                "api.interactions",
                "api.interactions.commands",
                "api.interactions.commands.build",
                "api.interactions.components",
                "api.interactions.components.buttons",
                "api.interactions.components.selections",
                "api.managers",
                "api.requests",
                "api.requests.restaction",
                "api.requests.restaction.order",
                "api.requests.restaction.pagination",
                "api.utils",
                "api.utils.cache",
            ),
            "java" to setOf(
                "io",
                "lang.management",
                "net",
                "text",
                "time",
                "time.format",
                "time.temporal",
                "util.concurrent",
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
                "system",
            ),
            "kotlinx" to setOf(
                "coroutines",
                "serialization",
                "serialization.json",
            ),
            "io.ktor" to setOf(
                "client",
                "client.call",
                "client.engine.cio",
                "client.plugins.contentnegotiation",
                "client.request",
                "http",
                "serialization.kotlinx.json",
            ),
            "org" to setOf("json", "jsoup", "reflections"),
            "mu" to emptySet(),
        )
    }
}