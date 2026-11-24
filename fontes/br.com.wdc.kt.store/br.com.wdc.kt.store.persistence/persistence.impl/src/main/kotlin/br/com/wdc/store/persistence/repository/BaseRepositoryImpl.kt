package br.com.wdc.store.persistence.repository

import br.com.wdc.store.persistence.StoreDSLContext
import org.jooq.DSLContext

/**
 * Base of the repositories over jOOQ.
 *
 * The repositories neither demarcate transactions nor take connections: the module's [DSLContext] uses the
 * current transaction's connection, or a standalone one in autocommit. Nor do they wrap exceptions — the real
 * one propagates, and the REST layer translates it.
 */
abstract class BaseRepositoryImpl {

    protected fun dsl(): DSLContext = StoreDSLContext.BEAN.get()

    /**
     * Tells whether a field goes into the `UPDATE`: the projection marks it (non-null value) and, when there is
     * a previous state, the value changed from it. Without [oldBean], every marked field is written — even with
     * `null`, which clears the value. That is why an `update` with neither projection nor `oldBean` passes the
     * empty entity as [oldBean]: only fields with a value go in, as in `insert`.
     */
    protected fun <E, V> changed(newBean: E, oldBean: E?, projection: E, getter: (E) -> V?): Boolean =
        getter(projection) != null && (oldBean == null || getter(newBean) != getter(oldBean))

    companion object {
        /** The module's `DSLContext`, for the mappings (`JsonQueryBuilder.setDSLContextSupplier`). */
        fun dsl(): DSLContext = StoreDSLContext.BEAN.get()
    }
}
