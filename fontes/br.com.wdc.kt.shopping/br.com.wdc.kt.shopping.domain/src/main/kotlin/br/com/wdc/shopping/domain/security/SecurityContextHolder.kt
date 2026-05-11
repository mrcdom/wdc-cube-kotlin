package br.com.wdc.shopping.domain.security

object SecurityContextHolder {

    private val HOLDER = ThreadLocal<SecurityContext>()

    fun get(): SecurityContext? = HOLDER.get()

    fun set(ctx: SecurityContext) = HOLDER.set(ctx)

    fun clear() = HOLDER.remove()
}
