package io.ileukocyte.hibernum.commands.developer

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.MessageContextOnlyCommand
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.openweather.Forecast
import io.ileukocyte.openweather.OpenWeatherApi

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.components.Modal
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import net.dv8tion.jda.api.utils.messages.MessageEditData

import org.jetbrains.kotlin.utils.addToStdlib.applyIf

import org.json.JSONObject

class EvalCommand : TextCommand, MessageContextOnlyCommand {
    override val name = "eval"
    override val contextName = "Execute Kotlin"
    override val description = "Executes the attached Kotlin code"
    override val aliases = setOf("exec", "execute", "kotlin")
    override val usages = setOf(setOf("Kotlin code".toClassicTextUsage()))

    private val packages get() = buildString {
        for ((key, value) in IMPORTS) {
            if (value.isNotEmpty()) {
                for (`package` in value) {
                    append("import $key.")

                    if (`package`.isNotEmpty()) {
                        append("$`package`.")
                    }

                    appendLine("*")
                }
            } else {
                appendLine("import $key.*")
            }
        }
    }

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val code = args?.applyIf(args.startsWith("```")) {
            removeSurrounding("```")
                .removePrefix("kts\n")
                .removePrefix("kt\n")
                .removePrefix("kotlin\n")
        } ?: throw NoArgumentsException

        try {
            val engine = Immutable.EVAL_KOTLIN_ENGINE

            with(engine.state.history) {
                if (isNotEmpty()) {
                    reset()
                }
            }

            engine.put("event", event)

            val result: Any? = engine.eval("""
                        |$packages
                        |
                        |$code
                    """.trimMargin())

            if (result !== null) {
                when (result) {
                    is Message -> event.channel.sendMessage(result.toCreateData()).queue()
                    is MessageCreateData -> event.channel.sendMessage(result).queue()
                    is MessageEditData -> event.channel.sendMessage(result.toCreateData()).queue()
                    is MessageEmbed -> event.channel.sendMessageEmbeds(result).queue()
                    is RestAction<*> -> result.queue()
                    is Array<*> -> event.channel.sendMessage(result.contentDeepToString()).queue()
                    is JSONObject -> event.channel.sendMessage(result.toString(2)).queue()
                    is Forecast -> event.channel.sendMessage(result.toString().remove(result.api.key)).queue()
                    is OpenWeatherApi -> event.channel.sendMessage(result.toString().remove(result.key)).queue()
                    else -> event.channel.sendMessage("$result").queue()
                }
            } else {
                event.channel.sendSuccess("Successful execution!").queue()
            }
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
            .create("$name-modal", "Kotlin Code Execution")
            .addActionRow(input)
            .build()

        event.replyModal(modal).queue()
    }

    override suspend fun invoke(event: ModalInteractionEvent) {
        val code = event.getValue("$name-code")?.asString ?: return
        val deferred = event.deferReply().await()

        val success = defaultEmbed("Successful execution!", EmbedType.SUCCESS)
        val failure = { t: Throwable ->
            defaultEmbed("""
                    |${t::class.simpleName ?: "An unknown exception"} has occurred:
                    |${t.message ?: "No message provided"}
                    |""".trimMargin(), EmbedType.FAILURE)
        }

        try {
            val engine = Immutable.EVAL_KOTLIN_ENGINE

            with(engine.state.history) {
                if (isNotEmpty()) {
                    reset()
                }
            }

            engine.put("event", event)

            val result: Any? = engine.eval("""
                        |$packages
                        |
                        |$code
                    """.trimMargin())

            if (result !== null) {
                when (result) {
                    is Message -> deferred.editOriginal(result.toEditData()).queue(null) {
                        event.messageChannel.sendMessage(result.toCreateData()).queue()
                    }
                    is MessageCreateData -> deferred.editOriginal(result.toEditData()).queue(null) {
                        event.messageChannel.sendMessage(result).queue()
                    }
                    is MessageEditData -> deferred.editOriginal(result).queue(null) {
                        event.messageChannel.sendMessage(result.toCreateData()).queue()
                    }
                    is MessageEmbed -> deferred.editOriginalEmbeds(result).queue(null) {
                        event.messageChannel.sendMessageEmbeds(result).queue()
                    }
                    is RestAction<*> -> result.queue({
                        deferred.editOriginalEmbeds(success).queue(null) {
                            event.messageChannel.sendMessageEmbeds(success).queue()
                        }
                    }) { t ->
                        deferred.editOriginalEmbeds(failure(t)).queue(null) {
                            event.messageChannel.sendMessageEmbeds(failure(t)).queue()
                        }
                    }
                    is Array<*> -> deferred.editOriginal(result.contentDeepToString()).queue(null) {
                        event.messageChannel.sendMessage(result.contentDeepToString()).queue()
                    }
                    is JSONObject -> deferred.editOriginal(result.toString(2)).queue(null) {
                        event.messageChannel.sendMessage(result.toString(2)).queue()
                    }
                    is Forecast -> deferred.editOriginal(result.toString().remove(result.api.key)).queue(null) {
                        event.messageChannel.sendMessage(result.toString().remove(result.api.key)).queue()
                    }
                    is OpenWeatherApi -> deferred.editOriginal(result.toString().remove(result.key)).queue(null) {
                        event.messageChannel.sendMessage(result.toString().remove(result.key)).queue()
                    }
                    else -> deferred.editOriginal("$result").queue(null) {
                        event.messageChannel.sendMessage("$result").queue()
                    }
                }
            } else {
                deferred.editOriginalEmbeds(success).queue(null) {
                    event.messageChannel.sendMessageEmbeds(success).queue()
                }
            }
        } catch (e: Exception) {
            deferred.editOriginalEmbeds(failure(e)).queue(null) {
                event.messageChannel.sendMessageEmbeds(failure(e)).queue()
            }
        }
    }

    override suspend fun invoke(event: MessageContextInteractionEvent) {
        val code = event.target.contentRaw.takeUnless { it.isEmpty() }
            ?.removePrefix("${Immutable.DEFAULT_PREFIX}$name ")
            ?.let { code ->
                code.applyIf(code.startsWith("```")) {
                    removeSurrounding("```")
                        .removePrefix("kts\n")
                        .removePrefix("kt\n")
                        .removePrefix("kotlin\n")
                }
            } ?: throw CommandException("No Kotlin code has been provided in the message!")

        val deferred = event.deferReply().await()

        val success = defaultEmbed("Successful execution!", EmbedType.SUCCESS)
        val failure = { t: Throwable ->
            defaultEmbed("""
                    |${t::class.simpleName ?: "An unknown exception"} has occurred:
                    |${t.message ?: "No message provided"}
                    |""".trimMargin(), EmbedType.FAILURE)
        }

        try {
            val engine = Immutable.EVAL_KOTLIN_ENGINE

            with(engine.state.history) {
                if (isNotEmpty()) {
                    reset()
                }
            }

            engine.put("event", event)

            val result: Any? = engine.eval("""
                        |$packages
                        |
                        |$code
                    """.trimMargin())

            if (result !== null) {
                when (result) {
                    is Message -> deferred.editOriginal(result.toEditData()).queue(null) {
                        event.messageChannel.sendMessage(result.toCreateData()).queue()
                    }
                    is MessageCreateData -> deferred.editOriginal(result.toEditData()).queue(null) {
                        event.messageChannel.sendMessage(result).queue()
                    }
                    is MessageEditData -> deferred.editOriginal(result).queue(null) {
                        event.messageChannel.sendMessage(result.toCreateData()).queue()
                    }
                    is MessageEmbed -> deferred.editOriginalEmbeds(result).queue(null) {
                        event.messageChannel.sendMessageEmbeds(result).queue()
                    }
                    is RestAction<*> -> result.queue({
                        deferred.editOriginalEmbeds(success).queue(null) {
                            event.messageChannel.sendMessageEmbeds(success).queue()
                        }
                    }) { t ->
                        deferred.editOriginalEmbeds(failure(t)).queue(null) {
                            event.messageChannel.sendMessageEmbeds(failure(t)).queue()
                        }
                    }
                    is Array<*> -> deferred.editOriginal(result.contentDeepToString()).queue(null) {
                        event.messageChannel.sendMessage(result.contentDeepToString()).queue()
                    }
                    is JSONObject -> deferred.editOriginal(result.toString(2)).queue(null) {
                        event.messageChannel.sendMessage(result.toString(2)).queue()
                    }
                    is Forecast -> deferred.editOriginal(result.toString().remove(result.api.key)).queue(null) {
                        event.messageChannel.sendMessage(result.toString().remove(result.api.key)).queue()
                    }
                    is OpenWeatherApi -> deferred.editOriginal(result.toString().remove(result.key)).queue(null) {
                        event.messageChannel.sendMessage(result.toString().remove(result.key)).queue()
                    }
                    else -> deferred.editOriginal("$result").queue(null) {
                        event.messageChannel.sendMessage("$result").queue()
                    }
                }
            } else {
                deferred.editOriginalEmbeds(success).queue(null) {
                    event.messageChannel.sendMessageEmbeds(success).queue()
                }
            }
        } catch (e: Exception) {
            deferred.editOriginalEmbeds(failure(e)).queue(null) {
                event.messageChannel.sendMessageEmbeds(failure(e)).queue()
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
                "hibernum.commands.moderation",
                "hibernum.commands.music",
                "hibernum.commands.utility",
                "hibernum.extensions",
                "hibernum.handlers",
                "hibernum.utils",
                "openweather",
                "openweather.entities",
                "openweather.extensions",
            ),
            "net.dv8tion.jda.api" to setOf(
                "",
                "audit",
                "entities",
                "entities.emoji",
                "entities.sticker",
                "events",
                "events.guild",
                "events.guild.voice",
                "events.interaction",
                "events.interaction.command",
                "events.interaction.component",
                "events.message",
                "events.message",
                "events.message.react",
                "exceptions",
                "hooks",
                "interactions",
                "interactions.commands",
                "interactions.commands.build",
                "interactions.components",
                "interactions.components.buttons",
                "interactions.components.selections",
                "interactions.components.text",
                "managers",
                "requests",
                "requests.restaction",
                "requests.restaction.order",
                "requests.restaction.pagination",
                "utils",
                "utils.cache",
                "utils.messages",
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
                "client.plugins.contentnegotiation",
                "client.request",
                "http",
                "serialization.kotlinx.json",
            ),
            "org" to setOf(
                "jetbrains.kotlin.util.collectionUtils",
                "jetbrains.kotlin.utils.addToStdlib",
                "json",
                "jsoup",
                "reflections",
            ),
            "mu" to emptySet(),
        )
    }
}