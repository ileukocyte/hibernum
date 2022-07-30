package io.ileukocyte.hibernum.commands.`fun`

import com.google.code.chatterbotapi.ChatterBot
import com.google.code.chatterbotapi.ChatterBotFactory
import com.google.code.chatterbotapi.ChatterBotSession
import com.google.code.chatterbotapi.ChatterBotType

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.hibernum.utils.*

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

import net.dv8tion.jda.api.entities.GuildMessageChannel
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.interactions.components.buttons.Button

import org.apache.commons.validator.routines.UrlValidator

import org.jetbrains.kotlin.utils.addToStdlib.applyIf

import org.jsoup.Jsoup

class ChomskyCommand : TextCommand {
    override val name = "chomsky"
    override val description = "Starts a chat session with the Chomsky bot"
    override val fullDescription = description.replace(
        "the Chomsky bot",
        "http://demo.vhost.pandorabots.com/pandora/talk?botid=$CHOMSKY_ID"
            .maskedLink("the Chomsky bot"),
    )
    override val aliases = setOf("chat", "chatbot", "chomsky-chat", "chomsky-bot")
    override val cooldown = 10L

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        if (event.author.processes.any { it.command is ChomskyCommand }) {
            throw CommandException("You have another Chomsky command running somewhere else! " +
                    "Finish the process first!")
        }

        val session = CHATTER_BOT?.createSession() ?: run {
            CHATTER_BOT = try {
                ChatterBotFactory().create(ChatterBotType.PANDORABOTS, CHOMSKY_ID)
            } catch (_: Exception) {
                null
            }

            throw CommandException("The bot failed to create a chat session!")
        }

        val staticProcessId = generateStaticProcessId(event.jda)

        event.channel.sendSuccess("The chat session has started! Say anything to the bot!") {
            text = "Type in \"exit\" to finish the session!"
        }.await().let {
            CHATTER_BOT_SESSIONS[event.author.idLong] = session

            awaitReply(event.author, event.guildChannel, it, session, staticProcessId)
        }
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        if (event.user.processes.any { it.command is ChomskyCommand }) {
            throw CommandException("You have another Chomsky command running somewhere else! " +
                    "Finish the process first!")
        }

        val session = CHATTER_BOT?.createSession() ?: run {
            CHATTER_BOT = try {
                ChatterBotFactory().create(ChatterBotType.PANDORABOTS, CHOMSKY_ID)
            } catch (_: Exception) {
                null
            }

            throw CommandException("The bot failed to create a chat session!")
        }

        val staticProcessId = generateStaticProcessId(event.jda)

        event.replySuccess("The chat session has started! Say anything to the bot!") {
            text = "Type in \"exit\" to finish the session!"
        }.await().retrieveOriginal().await().let {
            CHATTER_BOT_SESSIONS[event.user.idLong] = session

            awaitReply(event.user, event.guildChannel, it, session, staticProcessId)
        }
    }

    override suspend fun invoke(event: ButtonInteractionEvent) {
        val id = event.componentId.removePrefix("$interactionName-").split("-")

        if (event.user.id == id.first()) {
            when (id.last()) {
                "exit" -> {
                    CHATTER_BOT_SESSIONS -= event.user.idLong

                    event.jda.getProcessByEntities(event.user, event.channel)?.kill(event.jda)

                    event.editComponents().setSuccessEmbed("The chat session has been finished!") {
                        text = "This message will self-delete in 5 seconds"
                    }.queue({
                        it.deleteOriginal().queueAfter(5, TimeUnit.SECONDS, null) {}
                    }) { _ ->
                        event.channel.sendSuccess("The chat session has been finished!") {
                            text = "This message will self-delete in 5 seconds"
                        }.queue({
                            it.delete().queueAfter(5, TimeUnit.SECONDS, null) {}
                        }) {}
                    }
                }
                "stay" -> {
                    val session = CHATTER_BOT_SESSIONS[event.user.idLong] ?: return

                    val message = try {
                        event.editComponents()
                            .setSuccessEmbed("The chat session has been resumed!")
                            .await()
                            .retrieveOriginal()
                            .await()
                    } catch (_: ErrorResponseException) {
                        event.channel.sendSuccess("The chat session has been resumed!").await()
                    }

                    awaitReply(event.user, event.guildChannel, message, session, id[1].toInt())
                }
            }
        } else {
            throw CommandException("You did not invoke the initial command!")
        }
    }

    private suspend fun awaitReply(
        user: User,
        channel: GuildMessageChannel,
        message: Message,
        session: ChatterBotSession,
        processId: Int,
    ) {
        val reply = channel.awaitMessage(user, this, message, processId = processId)
            ?: return

        if (reply.contentRaw.lowercase() == "exit") {
            val confirmation = reply.replyConfirmation("Are you sure you want to exit?")
                .setActionRow(
                    Button.danger("$interactionName-${user.idLong}-exit", "Yes"),
                    Button.secondary("$interactionName-${user.idLong}-$processId-stay", "No"),
                ).await()

            channel.jda.awaitEvent<ButtonInteractionEvent>(waiterProcess = waiterProcess {
                this.channel = channel.idLong
                users += user.idLong
                command = this@ChomskyCommand
                invoker = confirmation.idLong
                id = processId
            }) { it.user.idLong == user.idLong && it.message == confirmation } // used to block other commands
        } else {
            val botReply = reply.replyEmbed {
                var response = session.think(reply.contentStripped).takeUnless { it.isEmpty() } ?: "\u2026"
                val links = Jsoup.parse(response).getElementsByTag("a")

                for (link in links) {
                    val url = link.attr("abs:href")

                    response = response.replace(
                        link.html(),
                        link.text().applyIf(UrlValidator.getInstance().isValid(url)) {
                            url.maskedLink(this)
                        },
                    )
                }

                description = Jsoup.parse(response).text()
                color = Immutable.SUCCESS
            }.await()

            awaitReply(user, channel, botReply, session, processId)
        }
    }

    companion object {
        private const val CHOMSKY_ID = "b0dafd24ee35a477"

        @JvmField
        internal var CHATTER_BOT: ChatterBot? = try {
            ChatterBotFactory().create(ChatterBotType.PANDORABOTS, CHOMSKY_ID)
        } catch (_: Exception) {
            null
        }

        @JvmField
        val CHATTER_BOT_SESSIONS = ConcurrentHashMap<Long, ChatterBotSession>()
    }
}