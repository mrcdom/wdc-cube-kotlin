package br.com.wdc.shopping.persistence.security

import br.com.wdc.shopping.domain.exception.AccessDeniedException
import br.com.wdc.shopping.domain.security.SecurityContext
import br.com.wdc.shopping.domain.security.SecurityContextHolder

internal object SecurityEnforcer {

    fun require(entity: String, operation: String): SecurityContext {
        val sc = SecurityContextHolder.get()
            ?: throw AccessDeniedException("Authentication required")
        if (!sc.hasPermission(entity, operation)) {
            throw AccessDeniedException("Requires $entity:$operation")
        }
        return sc
    }
}
