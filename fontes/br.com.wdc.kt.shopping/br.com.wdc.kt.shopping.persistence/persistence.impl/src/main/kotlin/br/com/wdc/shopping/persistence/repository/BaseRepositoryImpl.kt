package br.com.wdc.shopping.persistence.repository

import br.com.wdc.shopping.persistence.ShoppingDSLContext
import org.jooq.DSLContext

/**
 * Base dos repositórios sobre jOOQ.
 *
 * Os repositórios não demarcam transação nem pegam conexão: o [DSLContext] do módulo usa a conexão da
 * transação corrente, ou uma avulsa em autocommit. Também não embrulham exceções — a real sobe, e a camada
 * REST a traduz.
 */
abstract class BaseRepositoryImpl {

    protected fun dsl(): DSLContext = ShoppingDSLContext.BEAN.get()

    /**
     * Diz se um campo entra no `UPDATE`: a projeção o marca (valor não-nulo) e, havendo o estado anterior, o
     * valor mudou em relação a ele. Sem [oldBean], todo campo marcado é gravado — inclusive com `null`, que
     * limpa o valor.
     */
    protected fun <E, V> changed(newBean: E, oldBean: E?, projection: E, getter: (E) -> V?): Boolean =
        getter(projection) != null && (oldBean == null || getter(newBean) != getter(oldBean))

    companion object {
        /** O `DSLContext` do módulo, para os mapeamentos (`JsonQueryBuilder.setDSLContextSupplier`). */
        fun dsl(): DSLContext = ShoppingDSLContext.BEAN.get()
    }
}
