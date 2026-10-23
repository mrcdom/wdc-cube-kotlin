package br.com.wdc.framework.rest

import br.com.wdc.framework.rest.security.RestPrincipal
import io.javalin.http.Context
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.asContextElement

/**
 * Chave tipada de um atributo da requisição. Compare por identidade: crie uma instância por atributo, num
 * `companion object` ou num `object` da aplicação.
 */
class RequestKey<T : Any>(val name: String) {
    override fun toString(): String = "RequestKey($name)"
}

/**
 * O que se sabe da requisição corrente, acessível de qualquer camada por [current] — inclusive de dentro de
 * corrotinas, em qualquer thread: o pipeline das rotas instala o contexto como elemento da corrotina.
 *
 * Nasce antes dos filtros `before` e morre depois dos `after`. Os cabeçalhos são lidos da requisição; os
 * atributos são livres, para a aplicação guardar o que extrair dela (cliente, fuso, versão do app…).
 */
class RequestContext internal constructor(
    /** A requisição do Javalin. Para quem está na fronteira HTTP; camadas de baixo usam os métodos daqui. */
    val http: Context,
) {

    private val attributes = ConcurrentHashMap<RequestKey<*>, Any>()

    /** Verbo HTTP, em maiúsculas. */
    val method: String get() = http.method().name()

    /** Caminho pedido, sem a query. */
    val path: String get() = http.path()

    /** O modelo da rota que atendeu (`/api/user/{id}`), ou o caminho pedido se nenhuma rota casou. */
    val matchedPath: String get() = runCatching { http.endpoints().lastHttpEndpoint()?.path }.getOrNull() ?: http.path()

    /** Quem chama, depois da autenticação; `null` = anônimo, ou segurança desligada. */
    var principal: RestPrincipal? = null
        internal set

    /** O principal como o tipo da aplicação, ou `null` se anônimo ou de outro tipo. */
    inline fun <reified P : RestPrincipal> principalAs(): P? = principal as? P

    fun header(name: String): String? = http.header(name)

    fun headers(name: String): List<String> = http.req().getHeaders(name)?.toList() ?: emptyList()

    fun queryParam(name: String): String? = http.queryParam(name)

    fun pathParam(name: String): String? = http.pathParamMap()[name]

    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(key: RequestKey<T>): T? = attributes[key] as T?

    operator fun <T : Any> set(key: RequestKey<T>, value: T?) {
        if (value == null) attributes.remove(key) else attributes[key] = value
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrPut(key: RequestKey<T>, compute: () -> T): T = attributes.getOrPut(key, compute) as T

    /** Elemento de corrotina que mantém este contexto como o corrente, em qualquer thread em que ela retome. */
    fun asContextElement(): CoroutineContext.Element = CURRENT.asContextElement(this)

    companion object {
        private val CURRENT = ThreadLocal<RequestContext?>()

        /** O contexto da requisição corrente, ou `null` fora de uma requisição. */
        fun currentOrNull(): RequestContext? = CURRENT.get()

        /** O contexto da requisição corrente. Fora de uma requisição, [IllegalStateException]. */
        fun current(): RequestContext = CURRENT.get() ?: throw IllegalStateException("Não há requisição corrente")

        /**
         * Executa [block] com [context] como contexto corrente da thread, restaurando o anterior no fim. É o que
         * o pipeline faz em volta de cada rota; serve também a quem atende requisição fora dele.
         */
        fun <T> withCurrent(context: RequestContext?, block: () -> T): T {
            val previous = CURRENT.get()
            CURRENT.set(context)
            try {
                return block()
            } finally {
                if (previous == null) CURRENT.remove() else CURRENT.set(previous)
            }
        }

        internal fun of(ctx: Context): RequestContext = ctx.attributeOrCompute(ATTRIBUTE) { RequestContext(it) }!!

        internal const val ATTRIBUTE = "br.com.wdc.framework.rest.RequestContext"
    }
}
