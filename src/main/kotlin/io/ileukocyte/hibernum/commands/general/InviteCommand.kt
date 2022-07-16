package io.ileukocyte.hibernum.commands.general

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.extensions.await
import io.ileukocyte.hibernum.extensions.replyWarning
import io.ileukocyte.hibernum.extensions.sendWarning

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.buttons.Button

class InviteCommand : TextCommand {
    override val name = "invite"
    override val description = "Sends the link for inviting the bot to your server"
    override val options = setOf(
        OptionData(OptionType.BOOLEAN, "ephemeral", "Whether the response should be invisible to other users"))

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) =
        event.channel.sendWarning("In order to invite the bot, go to its profile " +
                "since the command is deprecated and will be removed " +
                "once the profile invitation button is supported on Discord on mobile!")
            .setActionRow(event.jda.let {
                linkButtonButton(it.selfUser.id, it.retrieveApplicationInfo().await().permissionsRaw)
            }).queue()

    override suspend fun invoke(event: SlashCommandInteractionEvent) =
        event.replyWarning("In order to invite the bot, go to its profile " +
                "since the command is deprecated and will be removed " +
                "once the profile invitation button is supported on Discord on mobile!")
            .addActionRow(event.jda.let {
                linkButtonButton(it.selfUser.id, it.retrieveApplicationInfo().await().permissionsRaw)
            }).setEphemeral(event.getOption("ephemeral")?.asBoolean ?: false).queue()

    private fun linkButtonButton(botId: String, permissions: Long) =
        Button.link(Immutable.INVITE_LINK_FORMAT.format(botId, permissions), "Invite Link")
}