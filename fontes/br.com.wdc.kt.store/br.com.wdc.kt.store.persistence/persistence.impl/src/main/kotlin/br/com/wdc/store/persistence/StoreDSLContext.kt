package br.com.wdc.store.persistence

import br.com.wdc.framework.commons.util.AtomicRef
import org.jooq.DSLContext

/**
 * Holder of the Store application's [DSLContext] (jOOQ).
 *
 * It belongs to the application — not to the framework — so that several applications in the same backend can
 * point to different databases without clashing in a global holder. `framework-jooq` receives this context by
 * injection (`JsonQueryBuilder.setDSLContextSupplier`).
 */
object StoreDSLContext {
    val BEAN = AtomicRef<DSLContext>()
}
