package com.example.tasks.data

import br.com.wdc.framework.commons.util.AtomicRef

/**
 * How a call reaches the backend: a `POST` of a JSON body to a path, answered with a JSON body. The calls are written
 * against this interface; the frontend registers its implementation (with the browser's `fetch`) in [BEAN], and a test
 * can register a fake.
 *
 * An implementation turns a refusal into the exception the backend threw: 400 `InvalidRequestException`, 404
 * `NotFoundException`, anything else `BusinessException`.
 */
interface CallHttpClient {

    companion object {
        val BEAN = AtomicRef<CallHttpClient>()
    }

    suspend fun post(path: String, json: String): String
}
