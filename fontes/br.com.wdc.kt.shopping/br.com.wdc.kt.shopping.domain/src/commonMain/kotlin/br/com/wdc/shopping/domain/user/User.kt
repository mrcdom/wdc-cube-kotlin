package br.com.wdc.shopping.domain.user

import br.com.wdc.framework.domain.codec.KeyedEntity

/**
 * Application user.
 *
 * The fields are nullable because `null` has two roles: missing data and, in a projection, "do not fetch this field".
 */
class User : KeyedEntity {
    var id: Long? = null
    var userName: String? = null

    /** The password digest, as stored in the database. Not part of the default projection. */
    var password: String? = null
    var name: String? = null

    /** Comma-separated roles (see `Role`). */
    var roles: String? = null

    override fun key(): Any? = id
}
