package br.com.wdc.shopping.persistence.rest.security

import br.com.wdc.framework.rest.security.RestPrincipal
import br.com.wdc.shopping.persistence.rest.ShoppingPrincipal

/** The requester's user id, as the row scopes compare it. */
internal fun RestPrincipal.userId(): Long =
    (this as? ShoppingPrincipal)?.context?.userId ?: id.toLongOrNull() ?: error("requester without a user id: $id")
