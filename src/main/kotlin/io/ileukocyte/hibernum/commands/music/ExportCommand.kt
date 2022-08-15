package io.ileukocyte.hibernum.commands.music

import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.audio.exportQueueAsJson
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.extensions.EmbedType
import io.ileukocyte.hibernum.extensions.defaultEmbed

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.utils.FileUpload

class ExportCommand : TextCommand {
    override val name = "export"
    override val description = "Sends the current queue and parameters of the player as a JSON file"
    override val aliases = setOf("export-queue", "save-queue")
    override val cooldown = 10L

    private val json = Json { prettyPrint = true }

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val audioPlayer = event.guild.audioPlayer ?: return

        if (audioPlayer.player.playingTrack === null) {
            throw CommandException("No track is currently playing!")
        }

        val queue = audioPlayer.exportQueueAsJson(false)
        val file = FileUpload.fromData(
            json.encodeToString(serializer(), queue).toByteArray(),
            "queue-${System.currentTimeMillis()}.json",
        )

        val embed = defaultEmbed("The queue has been exported as a file!", EmbedType.SUCCESS)

        event.channel.sendFiles(file).setEmbeds(embed).queue(null) { file.close() }
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val audioPlayer = event.guild?.audioPlayer ?: return

        if (audioPlayer.player.playingTrack === null) {
            throw CommandException("No track is currently playing!")
        }

        val queue = audioPlayer.exportQueueAsJson(false)
        val file = FileUpload.fromData(
            json.encodeToString(serializer(), queue).toByteArray(),
            "queue-${System.currentTimeMillis()}.json",
        )

        val embed = defaultEmbed("The queue has been exported as a file!", EmbedType.SUCCESS)

        event.replyFiles(file).setEmbeds(embed).queue(null) {
            event.channel.sendFiles(file).setEmbeds(embed).queue(null) {
                file.close()
            }
        }
    }
}