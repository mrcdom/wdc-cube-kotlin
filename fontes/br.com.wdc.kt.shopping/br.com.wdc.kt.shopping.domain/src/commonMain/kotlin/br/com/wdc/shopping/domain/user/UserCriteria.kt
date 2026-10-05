package br.com.wdc.shopping.domain.user

import br.com.wdc.framework.domain.criteria.ComparableCriterion
import br.com.wdc.framework.domain.criteria.Criteria
import br.com.wdc.framework.domain.criteria.Criterion
import br.com.wdc.framework.domain.criteria.TextCriterion

/**
 * Critério de pesquisa de [User].
 *
 * **A senha não é campo de critério**: não se pesquisa por ela, e tê-la aqui a exporia também na API. Quem
 * autentica projeta o resumo e o confere.
 *
 * Os campos nascem com o critério e nunca são `null`: para saber se um deles filtra, use `hasXxx()`.
 */
class UserCriteria : Criteria {

    var projection: User? = null
        private set

    fun withProjection(projection: User?) = apply { this.projection = projection }

    val userId = ComparableCriterion<UserCriteria, Long>(this, "userId")
    fun hasUserId(): Boolean = userId.isSet()
    fun withUserId(value: Long?): UserCriteria = if (value == null) this else userId.eq(value)

    val userName = TextCriterion(this, "userName")
    fun hasUserName(): Boolean = userName.isSet()
    fun withUserName(value: String?): UserCriteria = if (value == null) this else userName.eq(value)

    val name = TextCriterion(this, "name")
    fun hasName(): Boolean = name.isSet()
    fun withName(value: String?): UserCriteria = if (value == null) this else name.eq(value)

    val roles = TextCriterion(this, "roles")
    fun hasRoles(): Boolean = roles.isSet()
    fun withRoles(value: String?): UserCriteria = if (value == null) this else roles.eq(value)

    override fun criterions(): List<Criterion<*, *>> = listOf(userId, userName, name, roles)

    var orderBy: OrderBy? = null
        private set

    fun withOrderBy(orderBy: OrderBy?) = apply { this.orderBy = orderBy }

    /** Cada ordenação nomeia um efeito e tem um índice que a sustenta (ver `DBCreate`). */
    enum class OrderBy {
        OLDEST_FIRST,
        NEWEST_FIRST,
        NAME_A_TO_Z,
        LOGIN_A_TO_Z,
    }
}
