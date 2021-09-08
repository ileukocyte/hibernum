package io.ileukocyte.hibernum.commands.utility

import com.wrapper.spotify.SpotifyApi
import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.commands.CommandCategory
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.commands.TextOnlyCommand
import io.ileukocyte.hibernum.extensions.await
import io.ileukocyte.hibernum.extensions.sendEmbed
import io.ileukocyte.hibernum.extensions.toJSONObject
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.kotlin.utils.addToStdlib.cast
import org.json.JSONObject

class SpotifyCommand : TextOnlyCommand {
    override val name = "spotify"
    override val description = "search"
    override val category = CommandCategory.BETA

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        val api = SpotifyApi.Builder()
            .setClientId(Immutable.SPOTIFY_CLIENT_ID)
            .setClientSecret(Immutable.SPOTIFY_CLIENT_SECRET)
            .build()

        api.accessToken = api.clientCredentials().build().executeAsync().await().accessToken

        val request = api.searchTracks(args ?: throw NoArgumentsException).limit(5).build()
        val json = request.json.toJSONObject()
        val items = json.getJSONObject("tracks").getJSONArray("items")

        if (items.isEmpty)
            throw CommandException("No track has been found by the query!")

        event.channel.sendEmbed {
            for (track in items) {
                val obj = track as JSONObject

                appendln("[${obj.getJSONArray("artists").first().cast<JSONObject>().getString("name")} - ${obj.getString("name")} - ${obj.getJSONObject("album").getString("name")}](${obj.getJSONObject("external_urls").getString("spotify")})")
            }
        }.queue()
    }
}