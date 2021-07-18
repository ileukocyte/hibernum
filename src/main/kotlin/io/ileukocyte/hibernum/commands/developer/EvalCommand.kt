package io.ileukocyte.hibernum.commands.developer

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.commands.TextOnlyCommand
import io.ileukocyte.hibernum.extensions.sendSuccess

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
    override val usages = setOf("<Kotlin code>")

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        val code = args?.run {
            takeIf { it.startsWith("```") }
                ?.removeSurrounding("```")
                ?.removePrefix("kt\n")
                ?.removePrefix("kotlin\n")
                ?: this
        } ?: throw NoArgumentsException

        val packages = buildString {
            for ((key, value) in imports) {
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

            //with(engine.state.history) { if (isNotEmpty()) reset() }

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
        val imports = mutableMapOf(
            "io.ileukocyte" to setOf(
                "hibernum",
                "hibernum.annotations",
                "hibernum.builders",
                "hibernum.commands",
                "hibernum.commands.developer",
                "hibernum.commands.general",
                "hibernum.extensions",
                "hibernum.handlers",
                "hibernum.utils"
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
            "org" to setOf("json", "reflections"),
            "mu" to emptySet()
        )
    }
}