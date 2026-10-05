package br.com.wdc.shopping.persistence.client

interface HttpTransport {

    var accessTokenSupplier: (() -> String?)?

    /** Attempts to refresh the auth tokens. Returns true if successful. */
    var refreshHandler: (() -> Boolean)?

    /** Called when authentication fails irrecoverably (refresh also failed). */
    var onAuthFailure: (() -> Unit)?

    /**
     * A transação remota corrente. Quando devolve não-nulo, **todas** as chamadas autenticadas levam o
     * cabeçalho `X-Tx-Id` — inclusive `putBytes` —, e as escritas se juntam a essa transação no servidor.
     */
    var transactionIdSupplier: (() -> String?)?

    /**
     * Identificador desta instância de cliente, enviado em **todas** as chamadas (`X-Client-Id`). É o dono
     * das transações remotas quando não há usuário autenticado.
     */
    val clientId: String

    fun postJson(path: String, body: String): String

    fun postJsonNullable(path: String, body: String): String?

    fun postJsonPublic(path: String, body: String): String

    fun postJsonWithAuth(path: String, body: String, token: String): String

    fun getJson(path: String): String

    fun getBytes(path: String): ByteArray?

    fun putBytes(path: String, data: ByteArray): Boolean
}
