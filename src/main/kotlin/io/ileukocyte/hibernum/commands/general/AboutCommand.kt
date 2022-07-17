package io.ileukocyte.hibernum.commands.general

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.ClassicTextOnlyCommand
import io.ileukocyte.hibernum.commands.SlashOnlyCommand
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.hibernum.handlers.CommandHandler
import io.ileukocyte.hibernum.utils.asText

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDAInfo
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.Command.Type
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

class AboutCommand : TextCommand {
    override val name = "about"
    override val description = "Sends the bot's detailed technical statistics"
    override val options = setOf(
        OptionData(OptionType.BOOLEAN, "ephemeral", "Whether the response should be invisible to other users"))
    override val aliases = setOf("info", "stats")

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) =
        event.channel.sendMessageEmbeds(statsEmbed(event.jda, event.guild)).queue()

    override suspend fun invoke(event: SlashCommandInteractionEvent) =
        event.replyEmbeds(statsEmbed(event.jda, event.guild!!))
            .setEphemeral(event.getOption("ephemeral")?.asBoolean ?: false)
            .queue()

    private suspend fun statsEmbed(jda: JDA, guild: Guild) = buildEmbed {
        color = Immutable.SUCCESS
        thumbnail = jda.selfUser.effectiveAvatarUrl
        timestamp = jda.startDate

        description = buildString {
            val restPing = jda.restPing.await()
            val appInfo = jda.retrieveApplicationInfo().await()
            val inviteLink = Immutable.INVITE_LINK_FORMAT.format(jda.selfUser.id, appInfo.permissionsRaw)
            val musicStreamingServersCount = jda.guildCache
                .count { it.selfMember.voiceState?.inAudioChannel() == true }
            val owner = appInfo.owner
            val commandCount = setOf(
                CommandHandler.count { it is ClassicTextOnlyCommand }.takeIf { it > 0 }?.let { "text-only: $it" },
                CommandHandler.count { it is SlashOnlyCommand }.takeIf { it > 0 }?.let { "slash-only: $it" },
            )
            val discordCommands = jda.retrieveCommands().await()

            appendLine(appInfo.description
                .replace("Kotlin", "[Kotlin](https://kotlinlang.org/)")
                .removeSuffix(".")
            )
            appendLine()
            appendLine(musicStreamingServersCount.takeIf { it > 0 }
                ?.let { "Currently streaming music on **$it ${"server".singularOrPlural(it)}**" }
                ?: "Currently is not streaming any music on any of all the servers"
            )
            appendLine()
            appendLine("**[Invite Link]($inviteLink)** • **[GitHub Repository](${Immutable.GITHUB_REPOSITORY})**")
            appendLine("**Developer**: ${if (guild.isMember(owner)) owner.asMention else owner.asTag}")
            appendLine("**Bot Version**: ${Immutable.VERSION}")
            appendLine("**JDA Version**: ${JDAInfo.VERSION}")
            appendLine("**Discord Rest API Version**: ${JDAInfo.DISCORD_REST_VERSION}")
            appendLine("**Kotlin Version**: ${KotlinVersion.CURRENT}")
            appendLine("**Java Version**: ${System.getProperty("java.version") ?: "Unknown"}")
            appendLine("**Total Commands**: ${CommandHandler.size}" +
                    commandCount.filterNotNull().takeUnless { it.isEmpty() }
                        ?.let { " (${it.joinToString(", ")})" }.orEmpty())
            appendLine("**Slash Commands**: ${discordCommands.count { it.type == Type.SLASH }}")
            appendLine("**Message Context Commands**: ${discordCommands.count { it.type == Type.MESSAGE }}")
            appendLine("**User Context Commands**: ${discordCommands.count { it.type == Type.USER }}")
            append("**Servers**: ${jda.guildCache.size()}")

            if (jda.unavailableGuilds.isNotEmpty()) {
                append(" (${jda.unavailableGuilds.size} servers are unavailable)")
            }

            appendLine()
            appendLine("**Active Threads**: ${Thread.activeCount()}")
            appendLine("**Uptime**: ${asText(jda.uptime)}")
            appendLine("**Rest Ping**: $restPing ms")
            appendLine("**Gateway Ping**: ${jda.gatewayPing} ms")
        }

        author {
            name = jda.selfUser.name
            iconUrl = jda.selfUser.effectiveAvatarUrl
        }

        footer { text = "Last Reboot" }
    }
}