package br.com.wdc.shopping.domain.user

import br.com.wdc.framework.domain.criteria.ComparableCriterion
import br.com.wdc.framework.domain.criteria.Criteria
import br.com.wdc.framework.domain.criteria.Criterion
import br.com.wdc.framework.domain.criteria.TextCriterion

/**
 * Search criteria for [User].
 *
 * **The password is not a criteria field**: nobody searches by it, and having it here would also expose it in
 * the API. Whoever authenticates projects the digest and checks it.
 *
 * The fields are created with the criteria and are never `null`: to know whether one filters, use `hasXxx()`.
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

    /** Each ordering names an effect and has an index backing it (see `DBCreate`). */
    enum class OrderBy {
        OLDEST_FIRST,
        NEWEST_FIRST,
        NAME_A_TO_Z,
        LOGIN_A_TO_Z,
    }
}
