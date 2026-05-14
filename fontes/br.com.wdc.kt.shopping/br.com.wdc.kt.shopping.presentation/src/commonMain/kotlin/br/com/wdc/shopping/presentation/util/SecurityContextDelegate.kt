package br.com.wdc.shopping.presentation.util

import br.com.wdc.shopping.domain.security.SecurityContext
import br.com.wdc.shopping.domain.security.SecurityContextHolder

inline fun <T> withSecurityContext(contextSupplier: () -> SecurityContext?, block: () -> T): T {
    val previous = SecurityContextHolder.get()
    try {
        contextSupplier()?.let { SecurityContextHolder.set(it) }
        return block()
    } finally {
        if (previous != null) SecurityContextHolder.set(previous) else SecurityContextHolder.clear()
    }
}
