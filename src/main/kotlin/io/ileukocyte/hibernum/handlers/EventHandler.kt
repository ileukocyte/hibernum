package io.ileukocyte.hibernum.handlers

import io.ileukocyte.hibernum.audio.TrackUserData
import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.audio.stop
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.hibernum.utils.awaitEvent
import io.ileukocyte.hibernum.utils.getProcessByMessage
import io.ileukocyte.hibernum.utils.kill

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import net.dv8tion.jda.api.events.guild.voice.GuildVoiceJoinEvent
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageDeleteEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

import org.jetbrains.kotlin.utils.addToStdlib.cast

import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime

object EventHandler : ListenerAdapter() {
    override fun onSlashCommand(event: SlashCommandEvent) =
        CommandHandler(event)

    override fun onButtonClick(event: ButtonClickEvent) =
        CommandHandler(event)

    override fun onSelectionMenu(event: SelectionMenuEvent) =
        CommandHandler(event)

    override fun onGuildMessageReceived(event: GuildMessageReceivedEvent) =
        CommandHandler(event)

    @OptIn(ExperimentalTime::class)
    override fun onGuildVoiceLeave(event: GuildVoiceLeaveEvent) {
        if (event.member != event.guild.selfMember) {
            if (event.channelLeft == event.guild.selfMember.voiceState?.channel) {
                if (!event.channelLeft.members.any { !it.user.isBot }) {
                    if (event.guild.audioPlayer?.player?.playingTrack === null) {
                        event.guild.audioPlayer?.stop()
                        event.guild.audioManager.closeAudioConnection()
                    } else {
                        event.guild.audioPlayer?.player?.isPaused = true

                        CoroutineScope(CommandContext).launch {
                            try {
                                val joinEvent = event.jda.awaitEvent<GuildVoiceJoinEvent>(90, DurationUnit.SECONDS) {
                                    it.channelJoined == event.channelLeft && !it.member.user.isBot
                                }

                                delay(1000)

                                joinEvent?.guild?.audioPlayer?.player?.isPaused = false
                            } catch (e: TimeoutCancellationException) {
                                event.guild.audioPlayer?.player?.playingTrack?.userData
                                    ?.cast<TrackUserData>()
                                    ?.channel
                                    ?.let {
                                        it.sendWarning("${event.jda.selfUser.name} has been inactive for too long to stay in the voice channel! " +
                                            "The bot has left!",
                                            "This message will self-delete in 1 minute"
                                        ).queue({ w -> w.delete().queueAfter(1, DurationUnit.MINUTES, {}) {} }) {}
                                    }

                                event.guild.audioPlayer?.stop()
                                event.guild.audioManager.closeAudioConnection()
                            }
                        }
                    }
                }
            }
        } else event.guild.audioPlayer?.stop() // just in case
    }

    @OptIn(ExperimentalTime::class)
    override fun onGuildMessageDelete(event: GuildMessageDeleteEvent) {
        event.jda.getProcessByMessage(event.messageIdLong)?.let { process ->
            process.kill(event.jda)

            val description =
                "The ${process.command?.let { it::class.simpleName } ?: event.jda.selfUser.name} process " +
                        "running in this channel has been terminated via message deletion!"

            event.jda.getTextChannelById(process.channel)
                ?.sendMessage {
                    embeds += defaultEmbed(description, EmbedType.WARNING, "This message will self-delete in 5 seconds")

                    process.users.mapNotNull { event.jda.getUserById(it)?.asMention }.joinToString()
                        .takeUnless { it.isEmpty() }
                        ?.let { content += it }
                }?.queue({ it.delete().queueAfter(5, DurationUnit.SECONDS, {}) {} }, {})
        }
    }
}