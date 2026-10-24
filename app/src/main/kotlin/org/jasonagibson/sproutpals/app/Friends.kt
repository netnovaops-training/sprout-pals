package org.jasonagibson.sproutpals.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.jasonagibson.sproutpals.BuildConfig
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to the friends server (server/ in this repo), at SERVER_BASE/api.
 * Every call returns a [Reply]: the value, or a short sentence to show the person.
 * Nothing here throws - no network is a normal state for a phone.
 */
class Friends {
    @Serializable data class Registered(val code: String, val token: String)
    @Serializable data class Friend(val code: String, val species: String, val stage: String, val lastSeen: Long = 0)
    @Serializable data class Heart(val from: String, val message: String, val at: Long)
    @Serializable private data class Err(val error: String = "")
    /** A friend's pal staying in our garden ([until] in server seconds). */
    @Serializable data class Visitor(val code: String, val species: String, val stage: String, val outfit: String = "", val until: Long)
    /** Our pal out at [host]'s garden until [until] (server seconds). */
    @Serializable data class Away(val host: String, val until: Long, val species: String = "EGG", val stage: String = "EGG", val outfit: String = "")
    @Serializable data class GardenNow(val visitors: List<Visitor> = emptyList(), val away: Away? = null)

    sealed interface Reply<out T> {
        data class Ok<T>(val value: T) : Reply<T>
        data class Fail(val message: String) : Reply<Nothing>
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val base = "${BuildConfig.SERVER_BASE}/api"

    suspend fun register(): Reply<Registered> = call("POST", "/register", null, null) { json.decodeFromString(it) }

    suspend fun addFriend(token: String, code: String): Reply<Friend> =
        call("POST", "/friends", token, """{"code":${JsonPrimitive(code)}}""") { json.decodeFromString(it) }

    suspend fun friends(token: String): Reply<List<Friend>> = call("GET", "/friends", token, null) { json.decodeFromString(it) }

    suspend fun setPet(token: String, species: String, stage: String): Reply<Unit> =
        call("PUT", "/pet", token, """{"species":"$species","stage":"$stage"}""") { }

    suspend fun sendHeart(token: String, code: String, message: String): Reply<Unit> =
        call("POST", "/hearts", token, """{"code":${JsonPrimitive(code)},"message":${JsonPrimitive(message)}}""") { }

    suspend fun inbox(token: String): Reply<List<Heart>> = call("GET", "/inbox", token, null) { json.decodeFromString(it) }

    /** What the pal is wearing (shop ids), so it arrives dressed up when visiting. */
    suspend fun setOutfit(token: String, ids: List<String>): Reply<Unit> =
        call("PUT", "/outfit", token, """{"outfit":${JsonPrimitive(ids.joinToString(","))}}""") { }

    suspend fun visit(token: String, code: String, hours: Int): Reply<Away> =
        call("POST", "/visits", token, """{"code":${JsonPrimitive(code)},"hours":$hours}""") { json.decodeFromString(it) }

    suspend fun garden(token: String): Reply<GardenNow> = call("GET", "/garden", token, null) { json.decodeFromString(it) }

    private suspend fun <T> call(method: String, path: String, token: String?, body: String?, parse: (String) -> T): Reply<T> =
        withContext(Dispatchers.IO) {
            try {
                val conn = (URL(base + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 8_000
                    readTimeout = 10_000
                    setRequestProperty("Accept", "application/json")
                    if (token != null) setRequestProperty("Authorization", "Bearer $token")
                    if (body != null) {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        outputStream.use { it.write(body.toByteArray()) }
                    }
                }
                try {
                    val code = conn.responseCode
                    val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
                    if (code in 200..299) Reply.Ok(parse(text))
                    else Reply.Fail(runCatching { json.decodeFromString<Err>(text).error }.getOrNull()?.takeIf { it.isNotBlank() } ?: "The garden server said no ($code).")
                } finally {
                    conn.disconnect()
                }
            } catch (_: Exception) {
                Reply.Fail("Can't reach the garden server right now.")
            }
        }
}

/** The one-tap share text for sending your code to someone. */
fun shareText(code: String) = "Come play Sprout Pals with me! My garden code is $code"
