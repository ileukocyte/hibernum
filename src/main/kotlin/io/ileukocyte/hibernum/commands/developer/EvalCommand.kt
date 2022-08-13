package io.ileukocyte.hibernum.commands.developer

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.commands.*
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.openweather.Forecast
import io.ileukocyte.openweather.OpenWeatherApi

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
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
    override val options = setOf(
        OptionData(
            OptionType.BOOLEAN,
            "backup",
            "Whether the input should be backed up to the designated text channel (default is false)",
        ),
    )
    override val aliases = setOf("exec", "execute", "kotlin", "kt")
    override val usages = setOf(
        usageGroupOf("Kotlin code".toClassicTextUsage()),
        usageGroupOf("reply to a message with Kotlin code".toClassicTextUsage()),
    )

    private val packages get() = buildString {
        for ((key, value) in IMPORTS) {
            if (value.isNotEmpty()) {
                for (`import` in value.sortedBy { it.name }) {
                    append("import $key")

                    if (`import` is Entity) {
                        append(".${`import`.name}")

                        `import`.alternativeName?.let {
                            append(" as $it")
                        }

                        appendLine()
                    } else {
                        append(".")

                        if (`import`.name.isNotEmpty()) {
                            append("${`import`.name}.")
                        }

                        appendLine("*")
                    }
                }
            } else {
                appendLine("import $key.*")
            }

            appendLine()
        }
    }

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val code = args?.applyIf(args.startsWith("```")) {
            removeSurrounding("```")
                .removePrefix("kts\n")
                .removePrefix("kt\n")
                .removePrefix("kotlin\n")
        } ?: event.message.messageReference?.resolve()?.await()?.contentRaw?.let {
            var content = it

            for (prefix in aliases.plus(name).map { n -> "${Immutable.DEFAULT_PREFIX}$n" }) {
                content = content.removePrefix(prefix)
            }

            content.trim().applyIf(it.startsWith("```")) {
                removeSurrounding("```")
                    .removePrefix("kts\n")
                    .removePrefix("kt\n")
                    .removePrefix("kotlin\n")
            }
        }?.takeUnless { it.isEmpty() } ?: throw NoArgumentsException

        try {
            val engine = Immutable.EVAL_KOTLIN_ENGINE

            with(engine.state.history) {
                if (isNotEmpty()) {
                    reset()
                }
            }

            engine.put("event", event)

            val result: Any? = engine.eval(packages + code)

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
        val backup = event.getOption("backup")?.asBoolean ?: false

        val input = TextInput
            .create("$interactionName-code", "Enter Your Kotlin Code:", TextInputStyle.PARAGRAPH)
            .build()
        val modal = Modal
            .create("$interactionName-$backup", "Kotlin Code Execution")
            .addActionRow(input)
            .build()

        event.replyModal(modal).queue()
    }

    override suspend fun invoke(event: ModalInteractionEvent) {
        val code = event.getValue("$interactionName-code")?.asString ?: return
        val deferred = event.deferReply().await()

        Immutable.EVAL_MODAL_INPUT_BACKUP_CHANNEL_ID?.let { backup ->
            if (event.modalId.split("-").last().toBoolean()) {
                event.jda.getTextChannelById(backup)?.sendEmbed {
                    color = Immutable.SUCCESS
                    description = code.limitTo(MessageEmbed.DESCRIPTION_MAX_LENGTH - 9).codeblock("kt")

                    author {
                        name = "Kotlin Execution Input Backup"
                        iconUrl = event.jda.selfUser.effectiveAvatarUrl
                    }
                }?.queue()
            }
        }

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

            val result: Any? = engine.eval(packages + code)

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
        val code = event.target.contentRaw.takeUnless { it.isEmpty() }?.let { code ->
            var content = code

            for (prefix in aliases.plus(name).map { n -> "${Immutable.DEFAULT_PREFIX}$n" }) {
                content = content.removePrefix(prefix)
            }

            content.trim().applyIf(content.startsWith("```")) {
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

            val result: Any? = engine.eval(packages + code)

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
        val IMPORTS: MutableMap<String, Set<Import>> = mutableMapOf(
            "io.ileukocyte" to setOf(
                Package("hibernum"),
                Package("hibernum.audio"),
                Package("hibernum.builders"),
                Package("hibernum.commands"),
                Package("hibernum.commands.developer"),
                Package("hibernum.commands.`fun`"),
                Package("hibernum.commands.general"),
                Package("hibernum.commands.moderation"),
                Package("hibernum.commands.music"),
                Package("hibernum.commands.utility"),
                Package("hibernum.extensions"),
                Package("hibernum.handlers"),
                Package("hibernum.utils"),
                Package("openweather"),
                Package("openweather.entities"),
                Package("openweather.extensions"),
            ),
            "net.dv8tion.jda.api" to setOf(
                Package(),
                Package("audit"),
                Package("entities"),
                Package("entities.emoji"),
                Package("entities.sticker"),
                Package("events"),
                Package("events.guild"),
                Package("events.guild.voice"),
                Package("events.interaction"),
                Package("events.interaction.command"),
                Package("events.interaction.component"),
                Package("events.message"),
                Package("events.message.react"),
                Package("exceptions"),
                Package("hooks"),
                Package("interactions"),
                Package("interactions.commands"),
                Package("interactions.commands.build"),
                Package("interactions.components"),
                Package("interactions.components.buttons"),
                Package("interactions.components.selections"),
                Package("interactions.components.text"),
                Package("managers"),
                Package("requests"),
                Package("requests.restaction"),
                Package("requests.restaction.order"),
                Package("requests.restaction.pagination"),
                Package("utils"),
                Package("utils.cache"),
                Package("utils.messages"),
                Entity("entities.EmbedType", alternativeName = "JDAEmbedType"),
            ),
            "java" to setOf(
                Package("io"),
                Package("lang.management"),
                Package("net"),
                Package("text"),
                Package("time"),
                Package("time.format"),
                Package("time.temporal"),
                Package("util.concurrent"),
                Package("util.concurrent.atomic"),
                Entity("awt.Color"),
                Entity("util.Base64"),
                Entity("util.Date"),
                Entity("util.EnumSet"),
                Entity("util.Queue"),
            ),
            "kotlin" to setOf(
                Package("concurrent"),
                Package("coroutines"),
                Package("experimental"),
                Package("properties"),
                Package("random"),
                Package("reflect"),
                Package("reflect.full"),
                Package("reflect.jvm"),
                Package("system"),
                Package("time"),
                Entity("time.Duration.Companion.days"),
                Entity("time.Duration.Companion.hours"),
                Entity("time.Duration.Companion.microseconds"),
                Entity("time.Duration.Companion.milliseconds"),
                Entity("time.Duration.Companion.minutes"),
                Entity("time.Duration.Companion.nanoseconds"),
                Entity("time.Duration.Companion.seconds"),
            ),
            "kotlinx" to setOf(
                Package("coroutines"),
                Package("coroutines.future"),
                Package("serialization"),
                Package("serialization.json"),
            ),
            "io.ktor" to setOf(
                Package("client"),
                Package("client.call"),
                Package("client.plugins.contentnegotiation"),
                Package("client.request"),
                Package("http"),
                Package("serialization.kotlinx.json"),
            ),
            "org" to setOf(
                Package("jetbrains.kotlin.util.collectionUtils"),
                Package("jetbrains.kotlin.utils.addToStdlib"),
                Package("json"),
                Package("jsoup"),
                Package("reflections"),
            ),
            "mu" to emptySet(),
        )
    }

    sealed class Import(val name: String)
    class Package(name: String = ""): Import(name)
    class Entity(name: String, val alternativeName: String? = null): Import(name)
}