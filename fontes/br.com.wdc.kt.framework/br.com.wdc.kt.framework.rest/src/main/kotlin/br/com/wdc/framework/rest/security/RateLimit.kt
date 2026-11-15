package br.com.wdc.framework.rest.security

import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Limite de requisições por minuto de cada quem chama ([SecurityConfig.rateLimit]). Avaliado depois da autenticação
 * e antes da regra de acesso; pedido sem principal (uma rota pública chamada sem credencial) não conta.
 *
 * ```kotlin
 * security {
 *     authenticator = …
 *     rateLimit {
 *         key = { p -> p.id }
 *         perMinute = { p -> (p as Partner).requestsPerMinute }   // null = sem limite
 *     }
 * }
 * ```
 *
 * A janela é fixa, por minuto do relógio. Acima do limite, [RateLimitedException]: pelo mapeamento padrão, 429 com
 * `Retry-After` (os segundos até a virada do minuto); o `ExceptionMapping` da aplicação pode trocar a resposta.
 *
 * **A contagem é em memória, por instância:** com N instâncias atrás de um balanceador, o limite efetivo é até N
 * vezes o configurado.
 */
class RateLimitOptions {

    /** A chave da contagem. Padrão: o id do principal. */
    var key: (RestPrincipal) -> String = { it.id }

    /** O limite por minuto de quem chama. `null` = sem limite. Padrão: sem limite. */
    var perMinute: (RestPrincipal) -> Int? = { null }

    /** O relógio das janelas. Troque para teste. */
    var clock: Clock = Clock.systemUTC()
}

/** Quem chama passou do limite por minuto. 429 com `Retry-After` pelo mapeamento padrão. */
open class RateLimitedException(
    /** Os segundos até a janela seguinte, arredondados para cima. */
    val retryAfterSeconds: Long,
    message: String = "Too many requests",
) : RuntimeException(message)

/** A contagem: janela fixa por minuto, por chave. */
internal class RateLimiter(private val options: RateLimitOptions) {

    private class Window(val minute: Long) {
        val count = AtomicLong()
    }

    private val windows = ConcurrentHashMap<String, Window>()

    /** O minuto da última limpeza; as janelas mais velhas saem na primeira contagem de um minuto novo. */
    private val cleaned = AtomicLong(Long.MIN_VALUE)

    /** Conta o pedido de [principal]; acima do limite, [RateLimitedException]. */
    fun check(principal: RestPrincipal) {
        val limit = options.perMinute(principal) ?: return
        val now = options.clock.millis()
        val minute = Math.floorDiv(now, MINUTE)
        cleanUp(minute)
        val window = windows.compute(options.key(principal)) { _, current ->
            if (current == null || current.minute != minute) Window(minute) else current
        }!!
        if (window.count.incrementAndGet() > limit) {
            val remaining = MINUTE - Math.floorMod(now, MINUTE)
            throw RateLimitedException((remaining + 999) / 1000)
        }
    }

    internal val size: Int get() = windows.size

    private fun cleanUp(minute: Long) {
        val last = cleaned.get()
        if (last == minute || !cleaned.compareAndSet(last, minute)) return
        windows.entries.removeIf { it.value.minute < minute }
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
