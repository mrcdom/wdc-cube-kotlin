package br.com.wdc.store.persistence.rest.security

import br.com.wdc.framework.rest.security.RestPrincipal
import br.com.wdc.store.persistence.rest.StorePrincipal

/** The requester's user id, as the row scopes compare it. */
internal fun RestPrincipal.userId(): Long =
    (this as? StorePrincipal)?.context?.userId ?: id.toLongOrNull() ?: error("requester without a user id: $id")
