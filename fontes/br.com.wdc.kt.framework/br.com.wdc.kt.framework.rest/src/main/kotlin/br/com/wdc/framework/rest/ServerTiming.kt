package br.com.wdc.framework.rest

import java.util.Locale

/**
 * Opções do cabeçalho `Server-Timing` ([RestApi.Builder.serverTiming]), que o DevTools do navegador mostra na aba
 * Timing de cada pedido.
 *
 * Ligado, todo pedido que passa pela biblioteca sai com `total` (o pedido inteiro no servidor: autenticação, regra,
 * handler, commit e serialização), também nas respostas de erro, precedido das medidas que a rota registrar
 * ([RestCall.timing], [RestCall.timed]):
 * ```
 * Server-Timing: action;dur=12.3, total;dur=15.8
 * ```
 */
class ServerTimingOptions {

    /**
     * Inclui a descrição de cada medida (`desc="…"`). Desligado por padrão: em produção o cabeçalho não deve expor
     * nome interno de classe ou operação.
     */
    var names: Boolean = false
}

/** As medidas de um pedido, na ordem em que foram registradas. */
internal class ServerTiming(private val startNanos: Long, private val names: Boolean) {

    private class Entry(val name: String, val nanos: Long, val desc: String?)

    private val entries = ArrayList<Entry>(2)

    fun add(name: String, nanos: Long, desc: String?) {
        synchronized(entries) { entries += Entry(name, nanos, desc) }
    }

    /** O valor do cabeçalho, com `total` medido agora. */
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
