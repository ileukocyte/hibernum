package io.ileukocyte.hibernum.handlers

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.track.AudioTrack

import io.ileukocyte.hibernum.audio.*
import io.ileukocyte.hibernum.commands.`fun`.AkinatorCommand
import io.ileukocyte.hibernum.commands.`fun`.ChomskyCommand
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.hibernum.utils.WaiterContext
import io.ileukocyte.hibernum.utils.awaitEvent
import io.ileukocyte.hibernum.utils.getProcessByMessage
import io.ileukocyte.hibernum.utils.kill

import java.util.concurrent.TimeUnit

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select

import net.dv8tion.jda.api.entities.GuildMessageChannel
import net.dv8tion.jda.api.events.guild.GuildJoinEvent
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceJoinEvent
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceMoveEvent
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent
import net.dv8tion.jda.api.events.message.MessageDeleteEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

object EventHandler : ListenerAdapter() {
    override fun onMessageReceived(event: MessageReceivedEvent) =
        CommandHandler(event)

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) =
        CommandHandler(event)

    override fun onButtonInteraction(event: ButtonInteractionEvent) =
        CommandHandler(event)

    override fun onSelectMenuInteraction(event: SelectMenuInteractionEvent) =
        CommandHandler(event)

    override fun onModalInteraction(event: ModalInteractionEvent) =
        CommandHandler(event)

    override fun onCommandAutoCompleteInteraction(event: CommandAutoCompleteInteractionEvent) =
        CommandHandler(event)

    override fun onMessageContextInteraction(event: MessageContextInteractionEvent) =
        CommandHandler(event)

    override fun onUserContextInteraction(event: UserContextInteractionEvent) =
        CommandHandler(event)

    override fun onGuildVoiceJoin(event: GuildVoiceJoinEvent) {
        if (event.guild.selfMember.idLong == event.member.idLong) {
            val player = event.guild.audioPlayer ?: return

            if (player.player.playingTrack === null) {
                CoroutineScope(MusicContext).launch {
                    val deferred = CompletableDeferred<Unit>()

                    val listener = object : AudioEventAdapter() {
                        override fun onTrackStart(player: AudioPlayer, track: AudioTrack) {
                            player.removeListener(this)

                            deferred.complete(Unit)
                        }
                    }

                    player.player.addListener(listener)

                    try {
                        withTimeout((90).seconds) { deferred.await() }
                    } catch (_: TimeoutCancellationException) {
                        player.player.removeListener(listener)
                        player.stop()

                        if (event.member.voiceState?.inAudioChannel() == true) {
                            event.guild.audioManager.closeAudioConnection()
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
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

                            select {
                                eventsAwaited.forEach { deferred ->
                                    deferred.onAwait { e ->
                                        eventsAwaited.forEach {
                                            if (it.isActive) {
                                                it.cancelAndJoin()
                                            }
                                        }

                                        delay(1000)

                                        e?.guild?.audioPlayer?.player?.isPaused = false
                                    }
                                }

                                onTimeout((5).minutes.inWholeMilliseconds) {
                                    eventsAwaited.forEach {
                                        if (it.isActive) {
                                            it.cancelAndJoin()
                                        }
                                    }

                                    event.guild.audioPlayer?.player
                                        ?.playingTrack
                                        ?.customUserData
                                        ?.channel
                                        ?.let {
                                            it.sendWarning("${event.jda.selfUser.name} has been inactive for too long to stay in the voice channel! The bot has left!") {
                                                text = "This message will self-delete in a minute"
                                            }.queue({ w -> w.delete().queueAfter(1, TimeUnit.MINUTES, null) {} }) {}
                                        }

                                    event.guild.audioPlayer?.stop()
                                    event.guild.audioManager.closeAudioConnection()
                                }
                            }
                        }
                    }
                }
            }
        } else {
            event.guild.audioPlayer?.stop() // just in case
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
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

                        select {
                            eventsAwaited.forEach { deferred ->
                                deferred.onAwait { e ->
                                    eventsAwaited.forEach {
                                        if (it.isActive) {
                                            it.cancelAndJoin()
                                        }
                                    }

                                    delay(1000)

                                    e?.guild?.audioPlayer?.player?.isPaused = false
                                }
                            }

                            onTimeout((5).minutes.inWholeMilliseconds) {
                                eventsAwaited.forEach {
                                    if (it.isActive) {
                                        it.cancelAndJoin()
                                    }
                                }

                                event.guild.audioPlayer?.player
                                    ?.playingTrack
                                    ?.customUserData
                                    ?.channel
                                    ?.let {
                                        it.sendWarning("${event.jda.selfUser.name} has been inactive for too long to stay in the voice channel! The bot has left!") {
                                            text = "This message will self-delete in a minute"
                                        }.queue({ w -> w.delete().queueAfter(1, TimeUnit.MINUTES, null) {} }) {}
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

    override fun onMessageDelete(event: MessageDeleteEvent) {
        event.jda.getProcessByMessage(event.messageIdLong)?.let { process ->
            process.kill(event.jda)

            if (process.command is AkinatorCommand) {
                for (id in process.users) {
                    AkinatorCommand.AKIWRAPPERS -= id
                    AkinatorCommand.DECLINED_GUESSES -= id
                    AkinatorCommand.GUESS_TYPES -= id
                }
            }

            if (process.command is ChomskyCommand) {
                for (userId in process.users) {
                    ChomskyCommand.CHATTER_BOT_SESSIONS -= userId
                }
            }

            val description =
                "The ${process.command?.let { it::class.simpleName } ?: event.jda.selfUser.name} process " +
                        "running in this channel has been terminated via message deletion!"

            event.jda.getChannelById(GuildMessageChannel::class.java, process.channel)
                ?.sendMessage {
                    embeds += defaultEmbed(description, EmbedType.WARNING) {
                        text = "This message will self-delete in 10 seconds"
                    }

                    process.users.mapNotNull { event.jda.getUserById(it)?.asMention }.joinToString()
                        .takeUnless { it.isEmpty() }
                        ?.let { content += it }
                }?.queue({ it.delete().queueAfter(10, TimeUnit.SECONDS, null) {} }) {}
        }
    }

    override fun onGuildJoin(event: GuildJoinEvent) {
        MUSIC_MANAGERS[event.guild.idLong] = GuildMusicManager(PLAYER_MANAGER)
    }

    override fun onGuildLeave(event: GuildLeaveEvent) {
        MUSIC_MANAGERS -= event.guild.idLong
    }
}