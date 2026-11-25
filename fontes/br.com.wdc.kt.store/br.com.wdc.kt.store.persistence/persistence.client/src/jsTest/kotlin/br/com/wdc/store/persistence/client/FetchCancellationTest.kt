package br.com.wdc.store.persistence.client

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/** A call whose coroutine is cancelled aborts its `fetch`, and the cancellation goes up as itself. */
class FetchCancellationTest {

    @BeforeTest
    fun installFetchThatNeverAnswers() {
        js("globalThis.__savedFetch = globalThis.fetch")
        js("globalThis.fetch = function (url, init) { globalThis.__signal = init.signal; return new Promise(function () {}); }")
    }

    @AfterTest
    fun restoreFetch() {
        js("globalThis.fetch = globalThis.__savedFetch; globalThis.__signal = undefined")
    }

    @Test
    fun cancellingACall_abortsTheRequest() = runTest {
        val transport = JsHttpTransport("http://never.example")
        // a BusinessException instead of the cancellation would fail the test through the job
        val call = launch { transport.postJsonPublic("/api/auth/challenge", "{}") }
        runCurrent()
        assertTrue(js("globalThis.__signal != null && !globalThis.__signal.aborted") as Boolean, "the request is on its way")

        call.cancel()
        call.join()

        assertTrue(js("globalThis.__signal.aborted") as Boolean, "the request was aborted")
        assertTrue(call.isCancelled)
    }
}
