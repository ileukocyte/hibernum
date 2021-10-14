package io.ileukocyte.hibernum.commands.general

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.SlashOnlyCommand
import io.ileukocyte.hibernum.commands.TextOnlyCommand
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.hibernum.handlers.CommandHandler
import io.ileukocyte.hibernum.utils.asText

import kotlin.time.ExperimentalTime

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDAInfo
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent

class AboutCommand : Command {
    override val name = "about"
    override val description = "Sends the bot's detailed technical statistics"
    override val aliases = setOf("info", "stats")

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) =
        event.channel.sendMessageEmbeds(statsEmbed(event.jda)).queue()

    override suspend fun invoke(event: SlashCommandEvent) =
        event.replyEmbeds(statsEmbed(event.jda)).queue()

    @OptIn(ExperimentalTime::class)
    private suspend fun statsEmbed(jda: JDA) = buildEmbed {
        color = Immutable.SUCCESS
        thumbnail = jda.selfUser.effectiveAvatarUrl
        timestamp = jda.startDate

        description = buildString {
            val restPing = jda.restPing.await()
            val inviteLink = Immutable.INVITE_LINK_FORMAT.format(jda.selfUser.id)
            val musicStreamingServersCount = jda.guildCache
                .count { it.selfMember.voiceState?.inVoiceChannel() == true }

            appendLine("${jda.selfUser.name} is a modern Discord multi-purpose 100% [Kotlin](https://kotlinlang.org/)-coded bot that currently relies mostly on its musical functionality")
            appendLine()
            appendLine(
                musicStreamingServersCount.takeIf { it > 0 }
                    ?.let { "Currently streaming music on **$it ${"server".singularOrPlural(it)}**" }
                    ?: "Currently is not streaming any music on any of all the servers"
            )
            appendLine()
            appendLine("**[Invite Link]($inviteLink)** • **[GitHub Repository](${Immutable.GITHUB_REPOSITORY})**")
            appendLine("**Developer**: ${jda.retrieveApplicationInfo().await().owner.asTag}")
            appendLine("**Bot Version**: ${Immutable.VERSION}")
            appendLine("**JDA Version**: ${JDAInfo.VERSION}")
            appendLine("**Kotlin Version**: ${KotlinVersion.CURRENT}")
            appendLine("**Java Version**: ${System.getProperty("java.version") ?: "Unknown"}")
            appendLine("**Total Commands**: ${CommandHandler.size} " +
                    "(text-only: ${CommandHandler.count { it is TextOnlyCommand }}, " +
                    "slash-only: ${CommandHandler.count { it is SlashOnlyCommand }})")
            appendLine("**Discord-Side Slash Commands**: ${jda.retrieveCommands().await().size}")
            append("**Servers**: ${jda.guildCache.size()}")

            if (jda.unavailableGuilds.isNotEmpty())
                append(" (${jda.unavailableGuilds.size} servers are unavailable)")

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