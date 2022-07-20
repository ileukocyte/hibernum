package io.ileukocyte.hibernum.commands.general

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.extensions.*

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json

import kotlinx.serialization.json.*

import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.buttons.Button

class ChangelogCommand : TextCommand {
    override val name = "changelog"
    override val description = "Sends the changelog of the bot's latest stable version " +
            "available on its GitHub repository"
    override val options = setOf(
        OptionData(OptionType.BOOLEAN, "ephemeral", "Whether the response should be invisible to other users"))
    override val aliases = setOf("version")

    private val jsonSerializer = Json { ignoreUnknownKeys = true }
    private val client = Immutable.HTTP_CLIENT.config {
        install(ContentNegotiation) { json(jsonSerializer) }
    }

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val repository = Immutable.GITHUB_REPOSITORY.removePrefix("https://github.com/")
        val endpoint = "https://api.github.com/repos/$repository/releases"

        val release = client.get(endpoint).body<JsonArray>().first().jsonObject

        val releaseUrl = release["html_url"]?.jsonPrimitive?.content ?: return
        val version = release["name"]?.jsonPrimitive?.content ?: return
        val body = release["body"]?.jsonPrimitive?.content ?: return

        val changelog = "(#{2}\\s)((.*)([\\s\\S]+?)((?=#{2})))".toRegex()
            .findAll("$body##")
            .map { it.value }

        event.channel.sendEmbed {
            color = Immutable.SUCCESS

            for (changeGroup in changelog) {
                val lines = changeGroup.remove("\r").split("\n")

                appendLine(lines.first().removePrefix("## ").surroundWith("**"))
                append(lines.drop(1).joinToString("\n") {
                    var line = it

                    for (link in "\\[(`[^`]+`)]\\(([^)]+)\\)".toRegex().findAll(line)) {
                        line = line.replace(link.value, link.groupValues[1])
                    }

                    line.replace("^-".toRegex(), "\u2022")
                })
            }

            description = description?.limitTo(MessageEmbed.DESCRIPTION_MAX_LENGTH)

            author {
                name = version
                url = releaseUrl
                iconUrl = event.jda.selfUser.effectiveAvatarUrl
            }
        }.setActionRow(Button.link(Immutable.GITHUB_REPOSITORY, "GitHub Repository")).queue()
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val deferred = event.deferReply()
            .setEphemeral(event.getOption("ephemeral")?.asBoolean ?: false)
            .await()

        val repository = Immutable.GITHUB_REPOSITORY.removePrefix("https://github.com/")
        val endpoint = "https://api.github.com/repos/$repository/releases"

        val release = client.get(endpoint).body<JsonArray>().first().jsonObject

        val releaseUrl = release["html_url"]?.jsonPrimitive?.content ?: return
        val version = release["name"]?.jsonPrimitive?.content ?: return
        val body = release["body"]?.jsonPrimitive?.content ?: return

        val changelog = "(#{2}\\s)((.*)([\\s\\S]+?)((?=#{2})))".toRegex()
            .findAll("$body##")
            .map { it.value }

        val button = Button.link(Immutable.GITHUB_REPOSITORY, "GitHub Repository")
        val embed = buildEmbed {
            color = Immutable.SUCCESS

            for (changeGroup in changelog) {
                val lines = changeGroup.remove("\r").split("\n")

                appendLine(lines.first().removePrefix("## ").surroundWith("**"))
                append(lines.drop(1).joinToString("\n") {
                    var line = it

                    for (link in "\\[(`[^`]+`)]\\(([^)]+)\\)".toRegex().findAll(line)) {
                        line = line.replace(link.value, link.groupValues[1])
                    }

                    line.replace("^-".toRegex(), "\u2022")
                })
            }

            description = description?.limitTo(MessageEmbed.DESCRIPTION_MAX_LENGTH)

            author {
                name = version
                url = releaseUrl
                iconUrl = event.jda.selfUser.effectiveAvatarUrl
            }
        }

        deferred.editOriginalEmbeds(embed).setActionRow(button).queue(null) {
            event.channel.sendMessageEmbeds(embed).setActionRow(button).queue()
        }
    }
}
