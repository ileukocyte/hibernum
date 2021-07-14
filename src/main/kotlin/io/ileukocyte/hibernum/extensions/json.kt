@file:Suppress("UNUSED")
package io.ileukocyte.hibernum.extensions

import org.json.JSONArray
import org.json.JSONObject

inline fun jsonObject(lazy: JSONObject.() -> Unit) = JSONObject().apply(lazy)

inline fun <reified T> JSONObject.orNull(key: String) = opt(key) as? T

inline fun <T> JSONObject.put(lazy: () -> Pair<String, T>) {
    val (key, value) = lazy()
    put(key, value)
}

fun Collection<*>.toJSONArray() = JSONArray(this)

fun String.toJSONArray() = JSONArray(this)

fun String.toJSONArrayOrNull() = try {
    toJSONArray()
} catch (e: Exception) {
    null
}

fun String.toJSONObject() = JSONObject(this)

fun String.toJSONObjectOrNull() = try {
    toJSONObject()
} catch (e: Exception) {
    null
}