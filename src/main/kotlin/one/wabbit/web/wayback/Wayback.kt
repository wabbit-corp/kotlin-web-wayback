package one.wabbit.web.wayback

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.*

@Serializable
internal data class RawResponse(
    @SerialName("timestamp") val timestamp: String? = null,
    @SerialName("url") val url: String,
    @SerialName("archived_snapshots") val archivedSnapshots: ArchivedSnapshots
)

@Serializable
internal data class ArchivedSnapshots(@SerialName("closest") val closest: Entry? = null)

@Serializable
internal data class Entry(
    @SerialName("available") val available: Boolean,
    @SerialName("url") val url: String,
    @SerialName("timestamp") val timestamp: String,
    @SerialName("status") val status: String
)

data class ArchivedSnapshot(
    val available: Boolean,
    val timestamp: Instant,
    val url: String,
    val status: Int
)

data class Response(val result: ArchivedSnapshot?)

sealed class CheckError(message: String, cause: Throwable? = null) : Throwable(message, cause) {
    class FailedRequest(cause: Throwable) : CheckError("Failed request", cause)
    class InvalidJsonResponse(val response: String, cause: String) : CheckError("Could not parse response json")
}

internal fun parseTimestamp(s: String): Instant? {
    if (s.length != 14) return null
    if (!s.all { it.isDigit() }) return null

    // 20130919044612
    // 20190914221213
    // yyyyMMddHHmmSS
    // 4   2 2 6

    val year = s.substring(0, 4).toInt()
    val month = s.substring(4, 6).toInt()
    val day = s.substring(6, 8).toInt()
    val date = LocalDate.of(year, month, day)

    val hours = s.substring(8, 10).toInt()
    val minutes = s.substring(10, 12).toInt()
    val seconds = s.substring(12, 14).toInt()
    val time = LocalTime.of(hours, minutes, seconds)

    val dateTime = LocalDateTime.of(date, time)
    return dateTime.toInstant(ZoneOffset.UTC)
}

internal fun formatTimestamp(instant: Instant): String {
    val dt = LocalDateTime.ofInstant(instant, ZoneId.of("UTC"))
    return "${dt.year}${dt.monthValue}${dt.dayOfMonth}${dt.hour}${dt.minute}${dt.second}"
}

private const val AvailableCheckUrl = "http://archive.org/wayback/available"

object Wayback {
    suspend fun check(
        url: String,
        timestamp: Instant? = null,
        client: HttpClient? = null
    ): Response? {
        val connTimeoutMs = 10000
        val readTimeoutMs = 10000

        val client = client ?: HttpClient(CIO) {
            install(ContentNegotiation) {
                json()
            }
            install(HttpTimeout) { }
        }

        return try {
            var url = AvailableCheckUrl + "?url=${URLEncoder.encode(url, "UTF-8")}"
            if (timestamp != null) {
                url += "&timestamp=${formatTimestamp(timestamp)}"
            }

            val response = client.get(url) {
                timeout {
                    requestTimeoutMillis = connTimeoutMs.toLong()
                    socketTimeoutMillis = readTimeoutMs.toLong()
                }
            }.bodyAsText()

            val parsedResponse = Json.decodeFromString<RawResponse>(response)

            val closest = parsedResponse.archivedSnapshots.closest ?: return null
            val ts = parseTimestamp(closest.timestamp) ?: throw CheckError.InvalidJsonResponse(
                response.toString(),
                "invalid timestamp"
            )
            val status = closest.status.toIntOrNull() ?: throw CheckError.InvalidJsonResponse(
                response.toString(),
                "invalid status"
            )

            Response(ArchivedSnapshot(closest.available, ts, closest.url, status))
        } catch (e: Throwable) {
            if (e is CheckError) throw e
            throw CheckError.FailedRequest(e)
        }
    }

    suspend fun latest(
        url: String,
        client: HttpClient? = null
    ): Response? = check(
        url = url,
        timestamp = null,
        client = client
    )

    suspend fun closest(
        url: String,
        instant: Instant,
        client: HttpClient? = null
    ): Response? = check(
        url = url,
        timestamp = instant,
        client = client
    )
}
