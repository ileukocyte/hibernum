package io.ileukocyte.hibernum.commands.utility

import com.wrapper.spotify.SpotifyApi
import com.wrapper.spotify.model_objects.specification.Track

import de.androidpit.colorthief.ColorThief

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.*
import io.ileukocyte.hibernum.extensions.await
import io.ileukocyte.hibernum.extensions.limitTo
import io.ileukocyte.hibernum.extensions.replyEmbed
import io.ileukocyte.hibernum.extensions.sendEmbed
import io.ileukocyte.hibernum.utils.asDuration

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get

import net.dv8tion.jda.api.entities.Emoji
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.selections.SelectOption
import net.dv8tion.jda.api.interactions.components.selections.SelectionMenu

import java.awt.Color
import java.io.InputStream
import javax.imageio.ImageIO

import kotlin.math.round
import kotlin.time.ExperimentalTime

class SpotifyCommand : Command {
    override val name = "spotify"
    override val description = "Searches a Spotify track by the provided query and sends some information about one"
    override val category = CommandCategory.BETA
    override val usages = setOf("query")
    override val options = setOf(
        OptionData(OptionType.STRING, "query", "A search term", true)
    )
    override val cooldown = 5L

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        val query = args ?: throw NoArgumentsException
        val api = SPOTIFY_API

        api.accessToken = api.clientCredentials().build().executeAsync().await().accessToken

        val request = api.searchTracks(query).limit(5).build()
        val items = request.executeAsync().await().items.takeUnless { it.isEmpty() }
            ?: throw CommandException("No track has been found by the query!")

        if (items.size == 1) {
            event.channel.sendMessageEmbeds(trackEmbed(items.first(), api)).queue()
            return
        }

        val menu by lazy {
            val options = items.map {
                val artist = it.artists.first().name
                val title = it.name
                val album = it.album.name

                SelectOption.of("$artist - $title - $album".limitTo(100), it.id)
            }

            SelectionMenu.create("$name-${event.author.idLong}-spotify")
                .addOptions(
                    *options.toTypedArray(),
                    SelectOption.of("Exit", "exit").withEmoji(Emoji.fromUnicode("\u274C"))
                ).build()
        }

        event.channel.sendEmbed {
            color = Immutable.SUCCESS
            description = "Select a track you want to check information about!"
        }.setActionRow(menu).queue()
    }

    override suspend fun invoke(event: SlashCommandEvent) {
        val api = SPOTIFY_API

        api.accessToken = api.clientCredentials().build().executeAsync().await().accessToken

        val request = api.searchTracks(event.getOption("query")?.asString ?: return).limit(5).build()
        val items = request.executeAsync().await().items.takeUnless { it.isEmpty() }
            ?: throw CommandException("No track has been found by the query!")

        if (items.size == 1) {
            event.replyEmbeds(trackEmbed(items.first(), api)).queue()
            return
        }

        val menu by lazy {
            val options = items.map {
                val artist = it.artists.first().name
                val title = it.name
                val album = it.album.name

                SelectOption.of("$artist - $title - $album".limitTo(100), it.id)
            }

            SelectionMenu.create("$name-${event.user.idLong}-spotify")
                .addOptions(
                    *options.toTypedArray(),
                    SelectOption.of("Exit", "exit").withEmoji(Emoji.fromUnicode("\u274C"))
                ).build()
        }

        event.replyEmbed {
            color = Immutable.SUCCESS
            description = "Select a track you want to check information about!"
        }.addActionRow(menu).queue()
    }

    override suspend fun invoke(event: SelectionMenuEvent) {
        val id = event.componentId.removePrefix("$name-").split("-")

        if (event.user.id == id.first()) {
            if (event.selectedOptions?.firstOrNull()?.value == "exit") {
                event.message.delete().queue()
                return
            }

            if (id.last() == "spotify") {
                val api = SPOTIFY_API.apply {
                    accessToken = clientCredentials().build().executeAsync().await().accessToken
                }
                val track = api.getTrack(event.selectedOptions?.firstOrNull()?.value).build().executeAsync().await()

                try {
                    event.editComponents().setEmbeds(trackEmbed(track, api)).await()
                } catch (_: Exception) {
                    event.channel.sendMessageEmbeds(trackEmbed(track, api)).queue()
                }
            }
        } else throw CommandException("You did not invoke the initial command!")
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun trackEmbed(track: Track, api: SpotifyApi) = buildEmbed {
        val album = api.getAlbum(track.album.id).build().executeAsync().await()
        val artist = api.getArtist(track.artists.first().id).build().executeAsync().await()
        val features = api.getAudioFeaturesForTrack(track.id).build().executeAsync().await()

        album.images.maxByOrNull { it.height }?.url?.let { img ->
            image = img

            HttpClient(CIO).get<InputStream>(img).use {
                val bufferedImage = ImageIO.read(it)
                val rgb = ColorThief.getColor(bufferedImage)

                color = Color(rgb[0], rgb[1], rgb[2])
            }
        }

        author {
            name = "${track.artists.first().name} - ${track.name}"
            url = track.externalUrls.get("spotify")
            iconUrl = artist.images.firstOrNull()?.url
        }

        field {
            title = "Album"
            description = "[${album.name}](${album.externalUrls.get("spotify")})"
            isInline = true
        }

        field {
            title = "Duration"
            description = asDuration(track.durationMs.toLong())
            isInline = true
        }

        if (track.artists.size > 1) {
            field {
                title = "Featured Artists"
                description =
                    track.artists.filter { it.id != artist.id }.joinToString { "[${it.name}](${it.externalUrls.get("spotify")})" }
            }
        }

        if (album.tracks.total > 1) {
            field {
                title = "Track Number"
                description = "${track.trackNumber ?: "N/A"}"
                isInline = true
            }
        }

        field {
            title = "Release Date"
            description = album.releaseDate
            isInline = true
        }

        field {
            title = "Explicit"
            description = if (track.isExplicit) "Yes" else "No"
            isInline = true
        }

        field {
            title = "Popularity"
            description = track.popularity?.takeUnless { it == 0 }?.let { "$it/100" } ?: "N/A"
            isInline = true
        }

        features?.let {
            field {
                title = "Audio Features"
                description = """**Acousticness**: ${features.acousticness?.times(100)?.run { "${round(this).toInt()}%" } ?: "N/A"}
                    |**Danceability**: ${features.danceability?.times(100)?.run { "${round(this).toInt()}%" } ?: "N/A"}
                    |**Energy**: ${features.energy?.times(100)?.run { "${round(this).toInt()}%" } ?: "N/A"}
                    |**Instrumentalness**: ${features.instrumentalness?.times(100)?.run { "${round(this).toInt()}%" } ?: "N/A"}
                    |**Liveness**: ${features.liveness?.times(100)?.run { "${round(this).toInt()}%" } ?: "N/A"}
                    |**Speechiness**: ${features.speechiness?.times(100)?.run { "${round(this).toInt()}%" } ?: "N/A"}
                    |**Tempo**: ${features.tempo?.toInt()?.run { "$this BPM" } ?: "N/A"}
                    |**Valence**: ${features.valence?.times(100)?.run { "${round(this).toInt()}%" } ?: "N/A"}
                """.trimMargin()
            }
        }
    }

    companion object {
        @JvmField
        val SPOTIFY_API: SpotifyApi = SpotifyApi.Builder()
            .setClientId(Immutable.SPOTIFY_CLIENT_ID)
            .setClientSecret(Immutable.SPOTIFY_CLIENT_SECRET)
            .build()
    }
}