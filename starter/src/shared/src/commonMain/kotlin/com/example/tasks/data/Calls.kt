package com.example.tasks.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * What both sides of the calls agree on: where the backend answers them, and the JSON.
 *
 * A call (`calls/`, by area) is written here as a request over HTTP: its `run()` posts its `Request` to its `PATH`
 * and reads the answer. That is what the frontend uses. The backend extends each call with an `…Action` that
 * overrides `run()` to answer with the repository, and its route only reads the request and runs the action.
 */
object Calls {
    const val BASE_PATH = "/api/calls"

    /** The calls' JSON: fields the other side does not know are ignored, so the two can evolve one at a time. */
    val json = Json { ignoreUnknownKeys = true }

    /** Posts [request] to [path] through the registered [CallHttpClient] and reads the answer as [responseType]. */
    suspend fun <Q, R> post(path: String, request: Q, requestType: KSerializer<Q>, responseType: KSerializer<R>): R {
        val answer = CallHttpClient.BEAN.get().post(
            path = path,
            json = json.encodeToString(requestType, request),
        )
        return json.decodeFromString(responseType, answer)
    }
}
