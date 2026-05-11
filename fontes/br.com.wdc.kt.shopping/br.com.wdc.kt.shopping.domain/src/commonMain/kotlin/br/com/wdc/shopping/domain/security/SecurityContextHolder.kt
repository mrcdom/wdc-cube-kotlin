package br.com.wdc.shopping.domain.security

expect object SecurityContextHolder {
    fun get(): SecurityContext?
    fun set(ctx: SecurityContext)
    fun clear()
}
