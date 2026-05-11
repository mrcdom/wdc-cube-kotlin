package br.com.wdc.shopping.domain.criteria

import br.com.wdc.shopping.domain.model.User

class UserCriteria {
    var projection: User? = null
    var offset: Int? = null
    var limit: Int? = null
    var userId: Long? = null
    var userName: String? = null
    var password: String? = null
    var orderBy: OrderBy? = null

    fun withProjection(projection: User?) = apply { this.projection = projection }
    fun withOffset(offset: Int?) = apply { this.offset = offset }
    fun withLimit(limit: Int?) = apply { this.limit = limit }
    fun withUserId(userId: Long?) = apply { this.userId = userId }
    fun withUserName(userName: String?) = apply { this.userName = userName }
    fun withPassword(password: String?) = apply { this.password = password }
    fun withOrderBy(orderBy: OrderBy?) = apply { this.orderBy = orderBy }

    enum class OrderBy {
        ACENDING,
        DESCENDING
    }
}
