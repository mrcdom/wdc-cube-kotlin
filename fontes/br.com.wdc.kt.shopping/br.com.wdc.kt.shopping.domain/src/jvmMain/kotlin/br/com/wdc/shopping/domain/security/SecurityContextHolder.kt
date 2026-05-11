package br.com.wdc.shopping.domain.security

actual object SecurityContextHolder {

    private val HOLDER = ThreadLocal<SecurityContext>()

    actual fun get(): SecurityContext? = HOLDER.get()

    actual fun set(ctx: SecurityContext) = HOLDER.set(ctx)

    actual fun clear() = HOLDER.remove()
}
