package io.ileukocyte.hibernum.handlers

import io.ileukocyte.hibernum.audio.TrackUserData
import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.audio.stop
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.hibernum.utils.WaiterContext
import io.ileukocyte.hibernum.utils.awaitEvent
import io.ileukocyte.hibernum.utils.getProcessByMessage
import io.ileukocyte.hibernum.utils.kill

import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select

import net.dv8tion.jda.api.events.guild.voice.GuildVoiceJoinEvent
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceMoveEvent
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageDeleteEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

import org.jetbrains.kotlin.utils.addToStdlib.cast

object EventHandler : ListenerAdapter() {
    override fun onSlashCommand(event: SlashCommandEvent) =
        CommandHandler(event)

    override fun onButtonClick(event: ButtonClickEvent) =
        CommandHandler(event)

    override fun onSelectionMenu(event: SelectionMenuEvent) =
        CommandHandler(event)

    override fun onGuildMessageReceived(event: GuildMessageReceivedEvent) =
        CommandHandler(event)

    @OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
    override fun onGuildVoiceLeave(event: GuildVoiceLeaveEvent) {
        if (event.member != event.guild.selfMember) {
            if (event.channelLeft == event.guild.selfMember.voiceState?.channel) {
                if (!event.channelLeft.members.any { !it.user.isBot }) {
                    if (event.guild.audioPlayer?.player?.playingTrack === null) {
                        event.guild.audioPlayer?.stop()
                        event.guild.audioManager.closeAudioConnection()
                    } else {
                        event.guild.audioPlayer?.player?.isPaused = true

                        CoroutineScope(WaiterContext).launch {
                            val joinEventDeferred = async {
                                event.jda.awaitEvent<GuildVoiceJoinEvent> {
                                    it.channelJoined == event.channelLeft && !it.member.user.isBot
                                }
                            }

                            val moveEventDeferred = async {
                                event.jda.awaitEvent<GuildVoiceMoveEvent> {
                                    (it.channelJoined == event.channelLeft && !it.member.user.isBot)
                                        || (it.member == event.guild.selfMember
                                            && it.channelJoined.members.any { m -> !m.user.isBot })
                                }
                            }

                            val eventsAwaited = setOf(joinEventDeferred, moveEventDeferred)

                            select<Unit> {
                                eventsAwaited.forEach { deferred ->
                                    deferred.onAwait { e ->
                                        eventsAwaited.forEach { if (it.isActive) it.cancelAndJoin() }

                                        delay(1000)

                                        e?.guild?.audioPlayer?.player?.isPaused = false
                                    }
                                }

                                onTimeout(90000) {
                                    eventsAwaited.forEach { if (it.isActive) it.cancelAndJoin() }

                                    event.guild.audioPlayer?.player?.playingTrack?.userData
                                        ?.cast<TrackUserData>()
                                        ?.channel
                                        ?.let {
                                            it.sendWarning("${event.jda.selfUser.name} has been inactive for too long to stay in the voice channel! The bot has left!") {
                                                text = "This message will self-delete in a minute"
                                            }.queue({ w -> w.delete().queueAfter(1, DurationUnit.MINUTES, {}) {} }) {}
                                        }

                                    event.guild.audioPlayer?.stop()
                                    event.guild.audioManager.closeAudioConnection()
                                }
                            }
                        }
                    }
                }
            }
        } else event.guild.audioPlayer?.stop() // just in case
    }

    @OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
    override fun onGuildVoiceMove(event: GuildVoiceMoveEvent) {
        val vc = event.guild.selfMember.voiceState?.channel

        if (vc !== null) {
            if (vc.members.none { !it.user.isBot }) {
                if (event.guild.audioPlayer?.player?.playingTrack === null) {
                    event.guild.audioPlayer?.stop()
                    event.guild.audioManager.closeAudioConnection()
                } else {
                    event.guild.audioPlayer?.player?.isPaused = true

                    CoroutineScope(WaiterContext).launch {
                        val joinEventDeferred = async {
                            event.jda.awaitEvent<GuildVoiceJoinEvent> {
                                it.channelJoined == vc && !it.member.user.isBot
                            }
                        }

                        val moveEventDeferred = async {
                            event.jda.awaitEvent<GuildVoiceMoveEvent> {
                                (it.channelJoined == vc && !it.member.user.isBot)
                                    || (it.member == event.guild.selfMember
                                        && it.channelJoined.members.any { m -> !m.user.isBot })
                            }
                        }

                        val eventsAwaited = setOf(joinEventDeferred, moveEventDeferred)

                        select<Unit> {
                            eventsAwaited.forEach { deferred ->
                                deferred.onAwait { e ->
                                    eventsAwaited.forEach { if (it.isActive) it.cancelAndJoin() }

                                    delay(1000)

                                    e?.guild?.audioPlayer?.player?.isPaused = false
                                }
                            }

                            onTimeout(90000) {
                                eventsAwaited.forEach { if (it.isActive) it.cancelAndJoin() }

                                event.guild.audioPlayer?.player?.playingTrack?.userData
                                    ?.cast<TrackUserData>()
                                    ?.channel
                                    ?.let {
                                        it.sendWarning("${event.jda.selfUser.name} has been inactive for too long to stay in the voice channel! The bot has left!") {
                                            text = "This message will self-delete in a minute"
                                        }.queue({ w -> w.delete().queueAfter(1, DurationUnit.MINUTES, {}) {} }) {}
                                    }

                                event.guild.audioPlayer?.stop()
                                event.guild.audioManager.closeAudioConnection()
                            }
                        }
                    }
                }
            }
        }
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
                    embeds += defaultEmbed(description, EmbedType.WARNING) { text = "This message will self-delete in 5 seconds" }

                    process.users.mapNotNull { event.jda.getUserById(it)?.asMention }.joinToString()
                        .takeUnless { it.isEmpty() }
                        ?.let { content += it }
                }?.queue({ it.delete().queueAfter(5, DurationUnit.SECONDS, {}) {} }, {})
        }
    }
}