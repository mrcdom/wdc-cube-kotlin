package br.com.wdc.shopping.persistence

import br.com.wdc.framework.commons.util.AtomicRef
import org.jooq.DSLContext

/**
 * Holder do [DSLContext] (jOOQ) da aplicação Shopping.
 *
 * Pertence à aplicação — não ao framework — para que várias aplicações no mesmo backend possam apontar para
 * bancos distintos sem colidir num holder global. O `framework-jooq` recebe este contexto por injeção
 * (`JsonQueryBuilder.setDSLContextSupplier`).
 */
object ShoppingDSLContext {
    val BEAN = AtomicRef<DSLContext>()
}
