@file:Suppress("UNUSED")
package io.ileukocyte.hibernum.utils

import io.ileukocyte.hibernum.Immutable

import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

const val PERSPECTIVE_API_URL = "https://commentanalyzer.googleapis.com/v1alpha1/comments:analyze"

suspend fun getPerspectiveApiProbability(
    client: HttpClient,
    comment: String,
    mode: RequiredAttributes,
    isExperimentalMode: Boolean = false,
): Float {
    val requiredAttributes = mode.apply { isExperimental = isExperimentalMode }

    @Serializable
    data class Comment(val text: String)

    @Serializable
    data class Body(
        val comment: Comment,
        val requestedAttributes: JsonObject,
    )

    val response = client.post(PERSPECTIVE_API_URL) {
        parameter("key", Immutable.PERSPECTIVE_API_KEY)
        contentType(ContentType.Application.Json)
        setBody(Body(
            Comment(comment),
            buildJsonObject { putJsonObject(requiredAttributes.toString()) {} },
        ))
    }.apply {
        if (status != HttpStatusCode.OK) {
            throw IllegalStateException()
        }
    }.body<JsonObject>()

    val attributeScores = response["attributeScores"]?.jsonObject
    val attributes = attributeScores?.get(requiredAttributes.toString())?.jsonObject
    val summaryScore = attributes?.get("summaryScore")?.jsonObject
    val value = summaryScore?.get("value")?.jsonPrimitive

    return value?.floatOrNull ?: -1f
}

enum class RequiredAttributes(private val hasExperimentalImplPrefix: Boolean = true) {
    TOXICITY,
    SEVERE_TOXICITY,
    IDENTITY_ATTACK,
    INSULT,
    PROFANITY,
    THREAT,
    SEXUALLY_EXPLICIT(false),
    FLIRTATION(false);

    var isExperimental = false

    override fun toString() = name + "_EXPERIMENTAL"
        .takeIf { isExperimental && hasExperimentalImplPrefix }
        .orEmpty()
}