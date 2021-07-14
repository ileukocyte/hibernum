package io.ileukocyte.hibernum.commands.developer

import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.extensions.replyConfirmation
import io.ileukocyte.hibernum.extensions.replyFailure
import io.ileukocyte.hibernum.extensions.replySuccess
import io.ileukocyte.hibernum.extensions.sendConfirmation

import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.components.Button

import kotlin.system.exitProcess

class ShutdownCommand : Command {
    override val name = "shutdown"
    override val description = "The command shuts Hibernum down"
    //override val requiresButtonClick = true

    override suspend fun invoke(event: SlashCommandEvent) =
        event
            .replyConfirmation("Are you sure you want to shut the bot down?")
            .addActionRow(Button.danger("$name-${event.user.idLong}-shut", "Yes"))
            .addActionRow(Button.secondary("$name-${event.user.idLong}-exit", "No"))
            .queue()

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) =
        event.channel
            .sendConfirmation("Are you sure you want to shut the bot down?")
            .setActionRow(
                Button.danger("$name-${event.author.idLong}-shut", "Yes"),
                Button.secondary("$name-${event.author.idLong}-exit", "No")
            ).queue()

    override suspend fun invoke(event: ButtonClickEvent) {
        val id = event.componentId.removePrefix("$name-").split("-")

        if (event.user.id == id.first()) {
            when (id.last()) {
                "shut" -> {
                    event
                        .replySuccess("${event.jda.selfUser.name} has been successfully shut down!")
                        .setEphemeral(true)
                        .flatMap { event.message?.delete() }
                        .queue {
                            event.jda.shutdown()
                            exitProcess(0)
                        }
                }
                "exit" -> event.message?.delete()?.queue {
                    event.replySuccess("Successfully canceled!").setEphemeral(true).queue()
                }
            }
        } else event.replyFailure("You did not invoke the initial command!").setEphemeral(true).queue()
    }
}