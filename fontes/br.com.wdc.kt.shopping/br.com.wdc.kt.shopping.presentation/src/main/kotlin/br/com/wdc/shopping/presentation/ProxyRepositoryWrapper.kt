package br.com.wdc.shopping.presentation

import br.com.wdc.shopping.domain.security.SecurityContext
import br.com.wdc.shopping.domain.security.SecurityContextHolder
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.function.Supplier

object ProxyRepositoryWrapper {

    @Suppress("UNCHECKED_CAST")
    fun <T> wrap(repoInterface: Class<T>, delegate: T, contextSupplier: Supplier<SecurityContext?>): T? {
        if (delegate == null) return null
        return Proxy.newProxyInstance(
            repoInterface.classLoader,
            arrayOf(repoInterface),
            SecurityContextDelegate(delegate, contextSupplier)
        ) as T
    }

    private class SecurityContextDelegate(
        private val delegate: Any,
        private val contextSupplier: Supplier<SecurityContext?>,
    ) : InvocationHandler {

        override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
            val previous = SecurityContextHolder.get()
            try {
                val ctx = contextSupplier.get()
                if (ctx != null) {
                    SecurityContextHolder.set(ctx)
                }
                return if (args != null) method.invoke(delegate, *args) else method.invoke(delegate)
            } catch (e: InvocationTargetException) {
                throw e.cause!!
            } finally {
                if (previous != null) {
                    SecurityContextHolder.set(previous)
                } else {
                    SecurityContextHolder.clear()
                }
            }
        }
    }
}
