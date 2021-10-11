package io.ileukocyte.hibernum.commands.information

import de.androidpit.colorthief.ColorThief
import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.TextOnlyCommand
import io.ileukocyte.hibernum.extensions.searchMembers
import io.ileukocyte.hibernum.extensions.sendConfirmation
import io.ileukocyte.hibernum.extensions.sendEmbed
import io.ileukocyte.hibernum.extensions.surroundWith
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import net.dv8tion.jda.api.entities.Emoji
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.components.Button
import net.dv8tion.jda.api.interactions.components.selections.SelectOption
import net.dv8tion.jda.api.interactions.components.selections.SelectionMenu
import java.awt.Color
import java.io.InputStream
import javax.imageio.ImageIO

class UserCommand : TextOnlyCommand {
    override val name = "user"
    override val description = "N/A"
    override val aliases = setOf("userinfo")

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        if (args !== null) {
            val exception = CommandException("No users have been found by the query!")

            when {
                args matches Regex("\\d{17,20}") ->
                    chooseUserInfoOrPfp(event.guild.getMemberById(args)?.id ?: throw exception, event.author, event.channel)
                event.message.mentionedMembers.isNotEmpty() ->
                    chooseUserInfoOrPfp(event.message.mentionedMembers.firstOrNull()?.id ?: return, event.author, event.channel)
                args matches User.USER_TAG.toRegex() ->
                    chooseUserInfoOrPfp(event.guild.getMemberByTag(args)?.id ?: throw exception, event.author, event.channel)
                else -> {
                    val results = event.guild.searchMembers(args)
                        .takeUnless { it.isEmpty() }
                        ?.filterNotNull()
                        ?: throw exception

                    if (results.size == 1) {
                        chooseUserInfoOrPfp(results.first().id, event.author, event.channel)

                        return
                    }

                    val menu = SelectionMenu
                        .create("$name-${event.author.idLong}-search")
                        .addOptions(
                            *results.take(10).map { SelectOption.of(it.user.asTag, it.user.id) }.toTypedArray(),
                            SelectOption.of("Exit", "exit").withEmoji(Emoji.fromUnicode("\u274C"))
                        ).build()

                    event.channel.sendEmbed {
                        color = Immutable.SUCCESS
                        description = "Select the user you want to check information about!"
                    }.setActionRow(menu).queue()
                }
            }
        } else chooseUserInfoOrPfp(event.author.id, event.author, event.channel)
    }

    override suspend fun invoke(event: SelectionMenuEvent) {
        val id = event.componentId.removePrefix("$name-").split("-")

        if (event.user.id == id.first()) {
            val value = event.selectedOptions?.firstOrNull()?.value ?: return

            event.message.delete().queue()

            if (value == "exit")
                return

            chooseUserInfoOrPfp(value, event.user, event.textChannel)
        } else throw CommandException("You did not invoke the initial command!")
    }

    override suspend fun invoke(event: ButtonClickEvent) {
        val id = event.componentId.removePrefix("$name-").split("-")

        if (event.user.id == id.first()) {
            event.message.delete().queue()

            val type = id.last()

            if (type == "exit")
                return

            val member = event.guild?.getMemberById(id[1]) ?: return

            when (type) {
                // TODO
                "info" -> throw CommandException("This feature has not been implemented yet!")
                "pfp" -> event.channel.sendMessageEmbeds(pfp(member.user)).queue()
            }
        } else throw CommandException("You did not invoke the initial command!")
    }

    private fun chooseUserInfoOrPfp(id: String, author: User, channel: TextChannel) {
        val info = Button.secondary("$name-${author.idLong}-$id-info", "Information")
        val pfp = Button.secondary("$name-${author.idLong}-$id-pfp", "Profile Picture")
        val exit = Button.danger("$name-${author.idLong}-exit", "Exit")

        channel.sendConfirmation("Choose the type of information that you want to check!")
            .setActionRow(info, pfp, exit)
            .queue()
    }

    private suspend fun pfp(user: User) = buildEmbed {
        HttpClient(CIO).get<InputStream>(user.effectiveAvatarUrl).use {
            val bufferedImage = ImageIO.read(it)
            val rgb = ColorThief.getColor(bufferedImage)

            color = Color(rgb[0], rgb[1], rgb[2])
        }

        "${user.effectiveAvatarUrl}?size=2048".let { pfp ->
            author {
                name = user.asTag
                iconUrl = pfp
            }

            description = "[Profile Picture URL]($pfp)".surroundWith("**")
            image = pfp
        }
    }
}