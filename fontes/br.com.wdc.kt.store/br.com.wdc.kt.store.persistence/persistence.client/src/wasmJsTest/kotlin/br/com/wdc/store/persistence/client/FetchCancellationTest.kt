package br.com.wdc.store.persistence.client

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@JsFun("""() => {
    globalThis.__savedFetch = globalThis.fetch;
    globalThis.fetch = (url, init) => { globalThis.__signal = init.signal; return new Promise(() => {}); };
}""")
private external fun installFetchThatNeverAnswers()

@JsFun("() => { globalThis.fetch = globalThis.__savedFetch; globalThis.__signal = undefined; }")
private external fun restoreFetch()

@JsFun("() => globalThis.__signal != null")
private external fun requestSent(): Boolean

@JsFun("() => globalThis.__signal != null && globalThis.__signal.aborted")
private external fun requestAborted(): Boolean

/** A call whose coroutine is cancelled aborts its `fetch`, and the cancellation goes up as itself. */
class FetchCancellationTest {

    @BeforeTest
    fun setUp() = installFetchThatNeverAnswers()

    @AfterTest
    fun tearDown() = restoreFetch()

    @Test
    fun cancellingACall_abortsTheRequest() = runTest {
        val transport = WasmHttpTransport("http://never.example")
        // a BusinessException instead of the cancellation would fail the test through the job
        val call = launch { transport.postJsonPublic("/api/auth/challenge", "{}") }
        runCurrent()
        assertTrue(requestSent(), "the request is on its way")
        assertFalse(requestAborted())

        call.cancel()
        call.join()

        assertTrue(requestAborted(), "the request was aborted")
        assertTrue(call.isCancelled)
    }
}
