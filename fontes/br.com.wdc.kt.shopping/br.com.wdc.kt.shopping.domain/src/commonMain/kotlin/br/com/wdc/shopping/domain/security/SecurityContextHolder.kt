package br.com.wdc.shopping.domain.security

import br.com.wdc.framework.commons.concurrent.ThreadLocalRef

object SecurityContextHolder {

    private val HOLDER = ThreadLocalRef<SecurityContext>()

    fun get(): SecurityContext? = HOLDER.get()

    fun set(ctx: SecurityContext) = HOLDER.set(ctx)

    fun clear() = HOLDER.remove()
}
