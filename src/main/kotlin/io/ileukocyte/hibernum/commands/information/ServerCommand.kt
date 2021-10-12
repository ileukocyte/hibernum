package io.ileukocyte.hibernum.commands.information

import de.androidpit.colorthief.ColorThief
import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.TextOnlyCommand
import io.ileukocyte.hibernum.extensions.limitTo
import io.ileukocyte.hibernum.extensions.sendConfirmation
import io.ileukocyte.hibernum.extensions.surroundWith
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.components.Button
import java.awt.Color
import java.io.InputStream
import javax.imageio.ImageIO

class ServerCommand : TextOnlyCommand {
    override val name = "server"
    override val description = "Sends either detailed information about the server, its icon, or its list of custom emojis"
    override val aliases = setOf("guild", "guildinfo", "guild-info", "serverinfo", "server-info")
    override val cooldown = 5L

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        val buttons by lazy {
            val info = Button.secondary("$name-${event.author.idLong}-info", "Information")
            val icon = Button.secondary("$name-${event.author.idLong}-icon", "Server Icon")
                .takeUnless { event.guild.iconUrl === null }
            val emotes = Button.secondary("$name-${event.author.idLong}-emotes", "Custom Emojis")
                .takeUnless { event.guild.emoteCache.isEmpty }

            setOfNotNull(info, icon, emotes)
        }

        if (buttons.size == 1) {
            //event.channel.sendMessageEmbeds(infoEmbed(guild)).queue()

            //return
            throw CommandException()
        }

        event.channel.sendConfirmation("Choose the type of information that you want to check!")
            .setActionRow(
                *buttons.toTypedArray(),
                Button.danger("$name-${event.author.idLong}-exit", "Exit"),
            ).queue()
    }

    override suspend fun invoke(event: ButtonClickEvent) {
        val id = event.componentId.removePrefix("$name-").split("-")

        if (event.user.id == id.first()) {
            val type = id.last()

            if (type == "exit") {
                event.message.delete().queue()

                return
            }

            val guild = event.guild ?: return

            when (type) {
                "info" -> throw CommandException()//event.editMessageEmbeds(infoEmbed(guild)).setActionRows().queue()
                "icon" -> event.editMessageEmbeds(iconEmbed(guild)).setActionRows().queue()
                "emotes" -> event.editMessageEmbeds(emotesEmbed(guild)).setActionRows().queue()
            }
        } else throw CommandException("You did not invoke the initial command!")
    }

    private suspend fun infoEmbed(guild: Guild) = buildEmbed {
        guild.iconUrl?.let { icon ->
            HttpClient(CIO).get<InputStream>(icon).use {
                val bufferedImage = ImageIO.read(it)
                val rgb = ColorThief.getColor(bufferedImage)

                color = Color(rgb[0], rgb[1], rgb[2])
            }
        }
    }

    private suspend fun iconEmbed(guild: Guild) = buildEmbed {
        val icon = guild.iconUrl ?: return@buildEmbed

        HttpClient(CIO).get<InputStream>(icon).use {
            val bufferedImage = ImageIO.read(it)
            val rgb = ColorThief.getColor(bufferedImage)

            color = Color(rgb[0], rgb[1], rgb[2])
        }

        author {
            name = guild.name
            iconUrl = icon
        }

        "$icon?size=2048".let { hqIcon ->
            description = "[Server Icon URL]($hqIcon)".surroundWith("**")
            image = hqIcon
        }
    }

    private suspend fun emotesEmbed(guild: Guild) = buildEmbed {
        val emotes = guild.emoteCache.map { it.asMention }

        author {
            name = "Custom Emojis"
            iconUrl = guild.iconUrl
        }

        description = emotes.joinToString().let {
            if (it.length > MessageEmbed.DESCRIPTION_MAX_LENGTH) {
                it.limitTo(MessageEmbed.DESCRIPTION_MAX_LENGTH).let { l ->
                    if (!l.removePrefix("\u2026").endsWith(">")) {
                        l.split(", ").toMutableList().apply { removeLast() }
                            .joinToString() + "\u2026"
                    } else l
                }
            } else it
        }

        guild.iconUrl?.let { icon ->
            HttpClient(CIO).get<InputStream>(icon).use {
                val bufferedImage = ImageIO.read(it)
                val rgb = ColorThief.getColor(bufferedImage)

                color = Color(rgb[0], rgb[1], rgb[2])
            }
        }
    }
}