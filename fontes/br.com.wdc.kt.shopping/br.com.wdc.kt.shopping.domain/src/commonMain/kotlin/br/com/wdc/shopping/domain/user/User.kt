package br.com.wdc.shopping.domain.user

import br.com.wdc.framework.domain.codec.KeyedEntity

/**
 * Usuário da aplicação.
 *
 * Os campos são anuláveis porque `null` tem dois papéis: dado ausente e, numa projeção, "não traga este campo".
 */
class User : KeyedEntity {
    var id: Long? = null
    var userName: String? = null

    /** O resumo da senha, como está no banco. Não entra na projeção padrão. */
    var password: String? = null
    var name: String? = null

    /** Papéis separados por vírgula (ver `Role`). */
    var roles: String? = null

    override fun key(): Any? = id
}
