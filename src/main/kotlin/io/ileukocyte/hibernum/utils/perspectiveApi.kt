@file:Suppress("UNUSED")
package io.ileukocyte.hibernum.utils

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.extensions.jsonObject
import io.ileukocyte.hibernum.extensions.set
import io.ileukocyte.hibernum.extensions.toJSONObject

import io.ktor.client.*
import io.ktor.client.request.*

import org.json.JSONObject

suspend fun getPerspectiveApiProbability(
    client: HttpClient,
    comment: String,
    mode: RequiredAttributes,
    isExperimentalMode: Boolean = false,
): Float {
    val api = "https://commentanalyzer.googleapis.com/v1alpha1/comments:analyze"

    val requiredAttributes = mode.apply { isExperimental = isExperimentalMode }

    val response = client.post<String>(api) {
        val json = jsonObject {
            this["comment"] = jsonObject { this["text"] = comment }
            this["requestedAttributes"] = jsonObject { this[requiredAttributes.toString()] = JSONObject() }
        }

        body = json.toString()

        parameter("key", Immutable.PERSPECTIVE_API_KEY)
    }.toJSONObject()

    return response.getJSONObject("attributeScores")
        .getJSONObject(requiredAttributes.toString())
        .getJSONObject("summaryScore")
        .getFloat("value")
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