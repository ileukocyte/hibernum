package io.ileukocyte.hibernum.commands.general

import com.sun.management.OperatingSystemMXBean

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.*
import io.ileukocyte.hibernum.commands.GenericCommand.StaleComponentHandling
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.hibernum.handlers.CommandHandler
import io.ileukocyte.hibernum.utils.asText

import java.lang.management.ManagementFactory

import kotlin.math.max

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDAInfo
import net.dv8tion.jda.api.entities.ApplicationInfo
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.interactions.commands.Command.Type
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button

import org.jetbrains.kotlin.utils.addToStdlib.applyIf
import org.jetbrains.kotlin.utils.addToStdlib.cast

class AboutCommand : TextCommand {
    override val name = "about"
    override val description = "Sends the bot's detailed technical statistics"
    override val options = setOf(
        OptionData(OptionType.BOOLEAN, "ephemeral", "Whether the response should be invisible to other users"))
    override val aliases = setOf("bot-info", "info", "stats")
    override val staleComponentHandling = StaleComponentHandling.EXECUTE_COMMAND

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val appInfo = event.jda.retrieveApplicationInfo().await()

        val actionButtons = ActionRow.of(
            Button.primary("$interactionName-${event.author.idLong}-update", "Update"),
            Button.success("$interactionName-${event.author.idLong}-help", "Command Help"),
        )
        val linkButtons = ActionRow.of(
            Button.link(event.jda.getInviteUrl(appInfo.permissions), "Invite Link"),
            Button.link(Immutable.GITHUB_REPOSITORY, "GitHub Repository"),
        )

        event.channel.sendMessageEmbeds(statsEmbed(event.jda, appInfo, event.guild))
            .setComponents(actionButtons, linkButtons)
            .queue()
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val isEphemeral = event.getOption("ephemeral")?.asBoolean ?: false

        val deferred = event.deferReply().setEphemeral(isEphemeral).await()

        val appInfo = event.jda.retrieveApplicationInfo().await()

        val actionButtons = listOf(
            Button.primary("$interactionName-${event.user.idLong}-update", "Update"),
            Button.success("$interactionName-help", "Command Help"),
        ).applyIf(isEphemeral) { subList(1, size) }.let { ActionRow.of(it) }

        val linkButtons = ActionRow.of(
            Button.link(event.jda.getInviteUrl(appInfo.permissions), "Invite Link"),
            Button.link(Immutable.GITHUB_REPOSITORY, "GitHub Repository"),
        )

        val embed = statsEmbed(event.jda, appInfo, event.guild ?: return)

        deferred.editOriginalEmbeds(embed)
            .setComponents(actionButtons, linkButtons)
            .queue(null) {
                event.channel.sendMessageEmbeds(embed)
                    .setComponents(actionButtons, linkButtons)
                    .queue()
            }
    }

    override suspend fun invoke(event: ButtonInteractionEvent) {
        val id = event.componentId.removePrefix("$interactionName-").split("-")

        if (id.last() == "help") {
            val help = CommandHandler["help"].cast<HelpCommand>()
            val embed = help.getCommandList(
                jda = event.jda,
                author = event.user,
                isFromSlashCommand = true,
                isInDm = false,
            )

            event.replyEmbeds(embed).setEphemeral(true).queue(null) {}

            return
        }

        if (id.first() == event.user.id) {
            val deferred = event.deferEdit().await()
            val appInfo = event.jda.retrieveApplicationInfo().await()

            try {
                deferred.editOriginalEmbeds(statsEmbed(event.jda, appInfo, event.guild ?: return))
                    .await()
            } catch (_: ErrorResponseException) {
                event.channel.sendMessageEmbeds(statsEmbed(event.jda, appInfo, event.guild ?: return))
                    .queue()
            }
        } else {
            throw CommandException("You did not invoke the initial command!")
        }
    }

    private suspend fun statsEmbed(
        jda: JDA,
        appInfo: ApplicationInfo,
        guild: Guild,
    ) = buildEmbed {
        color = Immutable.SUCCESS
        thumbnail = jda.selfUser.effectiveAvatarUrl
        timestamp = jda.startDate

        description = buildString {
            val restPing = jda.restPing.await()
            val owner = appInfo.owner
            val commandCount = setOf(
                CommandHandler.count { it is ClassicTextOnlyCommand }.takeIf { it > 0 }
                    ?.let { "classic-text-only: $it" },
                CommandHandler.count { it is SlashOnlyCommand }.takeIf { it > 0 }
                    ?.let { "slash-only: $it" },
                CommandHandler.count { it is ContextCommand && it !is TextCommand }.takeIf { it > 0 }
                    ?.let { "context-menu-only: $it" },
            )
            val discordCommands = jda.retrieveCommands().await()
            val os = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean::class.java)

            val musicStreamingServersCount = jda.guildCache.count {
                it.selfMember.voiceState?.inAudioChannel() == true
                        && it.audioPlayer?.player?.playingTrack !== null
            }

            appendLine(appInfo.description
                .replace("Kotlin", "[Kotlin](https://kotlinlang.org/)")
                .removeSuffix(".")
            )
            appendLine()
            appendLine(musicStreamingServersCount.takeIf { it > 0 }
                ?.let {
                    "Music is currently being streamed via the bot on " +
                            "${it.toDecimalFormat("#,###")} ${"server".singularOrPlural(it)}".bold()
                } ?: "No music is currently being streamed via the bot on any of all the servers"
            )
            appendLine()
            appendLine("**Developer**: ${
                if (guild.isMember(owner)) {
                    owner.asMention
                } else {
                    owner.asTag
                }
            }")

            field {
                title = "Version Information"
                description = buildString {
                    appendLine("**Bot Version**: ${Immutable.VERSION}")
                    appendLine("**Discord Rest API Version**: ${JDAInfo.DISCORD_REST_VERSION}")
                    appendLine("**Discord Gateway Version**: ${JDAInfo.DISCORD_GATEWAY_VERSION}")
                    appendLine("**JDA Version**: ${JDAInfo.VERSION}")
                    appendLine("**Kotlin Version**: ${KotlinVersion.CURRENT}")
                    appendLine("**Java Version**: ${System.getProperty("java.version") ?: "Unknown"}")
                }
            }

            field {
                title = "Discord Statistics"
                description = buildString {
                    appendLine("**Total Commands**: ${CommandHandler.size}" +
                            commandCount.filterNotNull().takeUnless { it.isEmpty() }
                                ?.let { " (${it.joinToString(", ")})" }.orEmpty())
                    appendLine("**Slash Commands**: ${discordCommands.count { it.type == Type.SLASH }}")
                    appendLine("**Message Context Commands**: ${discordCommands.count { it.type == Type.MESSAGE }}")
                    appendLine("**User Context Commands**: ${discordCommands.count { it.type == Type.USER }}")
                    append("**Servers**: ${jda.guildCache.size().toDecimalFormat("#,###")}")

                    if (jda.unavailableGuilds.isNotEmpty()) {
                        append(" (${jda.unavailableGuilds.size} servers are unavailable)")
                    }
                }
            }

            field {
                title = "Runtime Statistics"
                description = buildString {
                    appendLine("**Uptime**: ${asText(jda.uptime)}")
                    appendLine("**Active Threads**: ${Thread.activeCount()}")
                    appendLine("**JVM CPU Usage**: ${max(0.0001, os.processCpuLoad)
                        .toDecimalFormat("###.##%")}")
                    appendLine("**CPU Cores**: ${os.availableProcessors}")
                    appendLine("**Operating System**: ${os.name} (${os.arch}, ${os.version})")
                }
            }

            field {
                title = "Latency Statistics"
                description = buildString {
                    appendLine("**Rest Ping**: ${restPing.toDecimalFormat("#,###")} ms")
                    appendLine("**Gateway Ping**: ${jda.gatewayPing.toDecimalFormat("#,###")} ms")
                }
            }
        }

        author {
            name = jda.selfUser.name
            url = Immutable.GITHUB_REPOSITORY
            iconUrl = jda.selfUser.effectiveAvatarUrl
        }

        footer { text = "Last Reboot" }
    }
}