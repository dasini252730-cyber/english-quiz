package com.englishquiz.app.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AiLearningClient(
    private val endpoint: String,
    private val connectTimeoutMillis: Int = 5_000,
    // Content generation is the slow call: the Edge Function gives the provider 60s, so the
    // read timeout sits above that. A socket timeout here would hide the function's own
    // error code, which is what tells the learner whether retrying is worth it.
    private val readTimeoutMillis: Int = 75_000,
) {
    suspend fun generateContent(request: ContentGenerationRequest): LearningContent =
        parseSafely(post(requestJson(request))) { parseContentResponse(it, request.mode) }

    suspend fun explainMeaning(request: MeaningRequest): ContextualMeaning =
        parseSafely(post(meaningJson(request)), ::parseMeaningResponse)

    private suspend fun post(body: JSONObject): JSONObject {
        val callerContext = currentCoroutineContext()
        return suspendCancellableCoroutine { continuation ->
        val connectionRef = AtomicReference<HttpURLConnection?>()
        // disconnect() is a no-op until the request has actually started, so a cancellation that
        // lands before then would otherwise leave the worker to run the full call. The flag lets
        // the worker see that cancellation itself and stop before opening the stream.
        val cancelled = AtomicBoolean(false)
        val worker = CoroutineScope(callerContext + Dispatchers.IO).launch {
            if (cancelled.get() || !continuation.isActive) return@launch
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = connectTimeoutMillis
                    readTimeout = readTimeoutMillis
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                }
                connectionRef.set(connection)
                val activeConnection = connection ?: return@launch
                if (cancelled.get() || !continuation.isActive) {
                    activeConnection.disconnect()
                    return@launch
                }
                activeConnection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val status = activeConnection.responseCode
                val stream = if (status in 200..299) activeConnection.inputStream else activeConnection.errorStream
                val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val json = runCatching { JSONObject(response) }
                    .getOrElse { throw AiLearningException("invalid_response", status) }
                if (status !in 200..299) {
                    throw AiLearningException(json.optJSONObject("error")?.optString("code") ?: "request_failed", status)
                }
                if (continuation.isActive) continuation.resume(json)
            } catch (error: AiLearningException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            } catch (_: CancellationException) {
                // The cancellation callback disconnects the blocking request.
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWithException(AiLearningException(errorCode(error), null))
            } finally {
                connectionRef.set(null)
                connection?.disconnect()
            }
        }
        continuation.invokeOnCancellation {
            cancelled.set(true)
            connectionRef.get()?.disconnect()
            worker.cancel()
        }
        }
    }

    private fun <T> parseSafely(response: JSONObject, parser: (JSONObject) -> T): T = try {
        parser(response)
    } catch (error: AiLearningException) {
        throw error
    } catch (_: Exception) {
        throw AiLearningException("invalid_response")
    }

    private fun requestJson(request: ContentGenerationRequest) = JSONObject().apply {
        put("action", "content")
        put("mode", request.mode.wireValue)
        put("difficulty", request.difficulty)
        put("reviewExpressions", JSONArray(request.reviewExpressions))
    }

    private fun meaningJson(request: MeaningRequest) = JSONObject().apply {
        put("action", "meaning")
        put("expression", request.expression)
        put("context", request.context)
    }

    private fun errorCode(error: Exception): String = when (error) {
        is SocketTimeoutException -> "timeout"
        else -> "network_error"
    }

}
