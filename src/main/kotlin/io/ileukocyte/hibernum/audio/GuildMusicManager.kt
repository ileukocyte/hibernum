package io.ileukocyte.hibernum.audio

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager

class GuildMusicManager(manager: AudioPlayerManager) {
    val player = manager.createPlayer()
    val scheduler = TrackScheduler(player)
    val sendHandler = AudioPlayerSendHandler(player)

    init {
        player.addListener(scheduler)
    }
}