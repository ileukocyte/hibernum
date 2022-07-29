package io.ileukocyte.hibernum.utils

import io.ileukocyte.hibernum.Immutable

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json

import kotlinx.serialization.json.*

const val WIKIPEDIA_API_BASE = "https://en.wikipedia.org/w/api.php"

internal val WIKIPEDIA_HTTP_CLIENT = Immutable.HTTP_CLIENT.config {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
}

suspend fun searchArticles(query: String, limit: Int): List<Pair<String, String>> {
    val response = WIKIPEDIA_HTTP_CLIENT.get(WIKIPEDIA_API_BASE) {
        parameter("action", "opensearch")
        parameter("search", query)
        parameter("limit", limit)
        parameter("namespace", 0)
        parameter("format", "json")
    }
    val json = response.body<JsonArray>()

    val titles = json[1].jsonArray.map { it.jsonPrimitive.content }

    val titlesToIds = if (titles.isNotEmpty()) {
        val pages = WIKIPEDIA_HTTP_CLIENT.get(WIKIPEDIA_API_BASE) {
            parameter("action", "query")
            parameter("titles", titles.joinToString("|"))
            parameter("format", "json")
        }.body<JsonObject>()["query"]?.jsonObject
            ?.get("pages")
            ?.jsonObject

        pages?.entries?.associate {
            it.value.jsonObject["title"]?.jsonPrimitive?.content to it.key
        } ?: emptyMap()
    } else {
        emptyMap()
    }

    return titles.map { it to titlesToIds[it]!! }
}

suspend fun getArticleInfo(id: Int): WikipediaArticle? {
    val response = WIKIPEDIA_HTTP_CLIENT.get(WIKIPEDIA_API_BASE) {
        parameter("action", "query")
        parameter("pageids", id)
        parameter("prop", "extracts|pageprops")
        parameter("exintro", true)
        parameter("explaintext", true)
        parameter("format", "json")
    }

    val pages = response.body<JsonObject>()["query"]?.jsonObject
        ?.get("pages")
        ?.jsonObject
        ?.values
        ?.filter { it.jsonObject["missing"] === null }
        ?.map {
            val pageJson = it.jsonObject

            val pageTitle = pageJson["title"]?.jsonPrimitive?.content.orEmpty()
            val pageDescription = pageJson["extract"]?.jsonPrimitive?.content.orEmpty()
            val pageThumbnailName = pageJson["pageprops"]?.jsonObject?.let { props ->
                props["page_image"]?.jsonPrimitive?.content
                    ?: props["page_image_free"]?.jsonPrimitive?.content
            }
            val pageThumbnail = pageThumbnailName?.let { name ->
                WIKIPEDIA_HTTP_CLIENT.get(WIKIPEDIA_API_BASE) {
                    parameter("action", "query")
                    parameter("titles", "File:${name.replace(".svg", ".png")}")
                    parameter("prop", "imageinfo")
                    parameter("iiprop", "url")
                    parameter("format", "json")
                }.body<JsonObject>()["query"]?.jsonObject?.get("pages")?.jsonObject?.values
                    ?.first()
                    ?.jsonObject
                    ?.get("imageinfo")
                    ?.jsonArray
                    ?.first()
                    ?.jsonObject
                    ?.get("url")
                    ?.jsonPrimitive
                    ?.content
            }

            WikipediaArticle(pageTitle, pageDescription, pageThumbnail)
        }

    return pages?.firstOrNull()
}

data class WikipediaArticle(
    val title: String,
    val description: String,
    val thumbnail: String? = null,
)