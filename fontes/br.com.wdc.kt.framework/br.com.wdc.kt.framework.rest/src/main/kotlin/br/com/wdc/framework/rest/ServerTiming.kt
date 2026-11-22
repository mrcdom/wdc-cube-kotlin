package br.com.wdc.framework.rest

import java.util.Locale

/**
 * Options of the `Server-Timing` header ([RestApi.Builder.serverTiming]), which the browser's DevTools shows in the
 * Timing tab of each request.
 *
 * When on, every request that goes through the library goes out with `total` (the whole request on the server:
 * authentication, rule, handler, commit and serialization), also on error responses, preceded by the measurements
 * the route records ([RestCall.timing], [RestCall.timed]):
 * ```
 * Server-Timing: action;dur=12.3, total;dur=15.8
 * ```
 */
class ServerTimingOptions {

    /**
     * Includes each measurement's description (`desc="…"`). Off by default: in production the header must not
     * expose internal class or operation names.
     */
    var names: Boolean = false
}

/** A request's measurements, in the order they were recorded. */
internal class ServerTiming(private val startNanos: Long, private val names: Boolean) {

    private class Entry(val name: String, val nanos: Long, val desc: String?)

    private val entries = ArrayList<Entry>(2)

    fun add(name: String, nanos: Long, desc: String?) {
        synchronized(entries) { entries += Entry(name, nanos, desc) }
    }

    /** The header value, with `total` measured now. */
    fun header(nowNanos: Long): String {
        val parts = synchronized(entries) { entries.map { metric(it.name, it.nanos, it.desc) } }
        return (parts + metric("total", nowNanos - startNanos, null)).joinToString(", ")
    }

    private fun metric(name: String, nanos: Long, desc: String?): String {
        val duration = String.format(Locale.ROOT, "%.1f", nanos / 1_000_000.0)
        return if (names && desc != null) "$name;desc=${quote(desc)};dur=$duration" else "$name;dur=$duration"
    }

    private fun quote(text: String): String = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    companion object {
        const val HEADER = "Server-Timing"
        val KEY = RequestKey<ServerTiming>("serverTiming")
    }
}
