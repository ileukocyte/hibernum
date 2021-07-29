package io.ileukocyte.hibernum.utils

import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.Video
import com.google.api.services.youtube.model.VideoContentDetails

import io.ileukocyte.hibernum.Immutable

import java.time.Duration

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

val YOUTUBE = YouTube.Builder(NetHttpTransport(), JacksonFactory()) {}
    .setApplicationName("hibernum-discord-bot")
    .build()

val YOUTUBE_LINK_REGEX =
    Regex("^(?:https?://)?(?:(?:www|m(?:usic)?)\\.)?(youtube\\.com|youtu.be)(/(?:[\\w\\-]+\\?v=|embed/|v/)?)([\\w\\-]+)(\\S+)?$")

suspend fun searchVideos(query: String, maxResults: Long = 15): List<Video> = suspendCoroutine {
    val search = YOUTUBE.search().list("id,snippet")

    search.key = Immutable.YOUTUBE_API_KEY
    search.q = query
    search.maxResults = maxResults
    search.fields = "items(id/kind,id/videoId,snippet/title,snippet/thumbnails/default/url)"
    search.type = "video"

    val videos = YOUTUBE.videos().list("id,snippet,contentDetails")

    videos.key = search.key
    videos.id = search.execute().items.joinToString(",") { v -> v.id.videoId }

    it.resume(videos.execute().items)
}

val VideoContentDetails.durationInMillis get() = Duration.parse(duration).toMillis()