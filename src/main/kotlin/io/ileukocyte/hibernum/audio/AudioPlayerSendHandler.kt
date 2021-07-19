package io.ileukocyte.hibernum.audio

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame

import net.dv8tion.jda.api.audio.AudioSendHandler

import java.nio.ByteBuffer

class AudioPlayerSendHandler(private val player: AudioPlayer) : AudioSendHandler {
    private val buffer = ByteBuffer.allocate(StandardAudioDataFormats.DISCORD_OPUS.maximumChunkSize())
    private val frame = MutableAudioFrame().apply { setBuffer(buffer) }

    override fun canProvide() = player.provide(frame)
    override fun isOpus() = true
    override fun provide20MsAudio() = buffer?.flip()
}