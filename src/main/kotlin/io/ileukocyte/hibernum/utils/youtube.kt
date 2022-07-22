@file:JvmName("YouTubeUtils")
package io.ileukocyte.hibernum.utils

import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.Playlist
import com.google.api.services.youtube.model.Video
import com.google.api.services.youtube.model.VideoContentDetails

import io.ileukocyte.hibernum.Immutable

import java.time.Duration

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

val YOUTUBE: YouTube = YouTube.Builder(NetHttpTransport(), GsonFactory()) {}
    .setApplicationName("hibernum-discord-bot")
    .build()

val YOUTUBE_LINK_REGEX =
    Regex("^(?:https?://)?(?:(?:www|m(?:usic)?)\\.)?(youtube\\.com|youtu.be)(/(?:[\\w\\-]+\\?v=|embed/|v/)?)([\\w\\-]+)(\\S+)?$")

suspend fun searchVideos(query: String, maxResults: Long = 15): List<Video> = suspendCoroutine {
    val search = YOUTUBE.search().list(listOf("id", "snippet"))

    search.key = Immutable.YOUTUBE_API_KEY
    search.q = query
    search.maxResults = maxResults
    search.fields = "items(id/kind,id/videoId,snippet/title,snippet/thumbnails/default/url)"
    search.type = listOf("video")

    val videos = YOUTUBE.videos().list(listOf("id", "snippet", "contentDetails"))

    videos.key = search.key
    videos.id = listOf(search.execute().items.joinToString(",") { v -> v.id.videoId })

    it.resume(videos.execute().items)
}

suspend fun searchPlaylists(query: String, maxResults: Long = 15): List<Playlist> = suspendCoroutine {
    val search = YOUTUBE.search().list(listOf("id", "snippet"))

    search.key = Immutable.YOUTUBE_API_KEY
    search.q = query
    search.maxResults = maxResults
    search.type = listOf("playlist")

    val playlists = YOUTUBE.playlists().list(listOf("id", "snippet", "contentDetails"))

    playlists.key = search.key
    playlists.id = listOf(search.execute().items.joinToString(",") { p -> p.id.playlistId })

    it.resume(playlists.execute().items)
}

val VideoContentDetails.durationInMillis get() = Duration.parse(duration).toMillis()