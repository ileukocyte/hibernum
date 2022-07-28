package io.ileukocyte.hibernum.audio

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager

class GuildMusicManager(manager: AudioPlayerManager) {
    val player: AudioPlayer = manager.createPlayer()
    val scheduler = TrackScheduler(player)
    val sendingHandler = AudioPlayerSendHandler(player)

    init {
        player.addListener(scheduler)
    }
}