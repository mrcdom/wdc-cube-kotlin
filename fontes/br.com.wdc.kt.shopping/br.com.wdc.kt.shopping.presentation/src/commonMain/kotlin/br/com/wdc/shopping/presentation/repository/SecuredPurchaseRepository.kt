package br.com.wdc.shopping.presentation.repository

import br.com.wdc.shopping.domain.purchase.Purchase
import br.com.wdc.shopping.domain.purchase.PurchaseCriteria
import br.com.wdc.shopping.domain.purchase.PurchaseRepository
import br.com.wdc.shopping.domain.security.SecurityContext
import br.com.wdc.shopping.presentation.util.withSecurityContext

/**
 * Executa as operações com o contexto de segurança da aplicação. `fetchById`, `fetchPage` e `insertOrUpdate`
 * vêm dos defaults da interface, que passam pelas operações abaixo.
 */
class SecuredPurchaseRepository(
    private val delegate: PurchaseRepository,
    private val contextSupplier: () -> SecurityContext?,
) : PurchaseRepository {

    override fun newProjection(): Purchase = delegate.newProjection()

    override suspend fun insert(bean: Purchase) =
        withSecurityContext(contextSupplier) { delegate.insert(bean) }

    override suspend fun update(newBean: Purchase, oldBean: Purchase?, projection: Purchase?) =
        withSecurityContext(contextSupplier) { delegate.update(newBean, oldBean, projection) }

    override suspend fun delete(criteria: PurchaseCriteria) =
        withSecurityContext(contextSupplier) { delegate.delete(criteria) }

    override suspend fun count(criteria: PurchaseCriteria) =
        withSecurityContext(contextSupplier) { delegate.count(criteria) }

    override suspend fun fetch(criteria: PurchaseCriteria, offset: Int, limit: Int) =
        withSecurityContext(contextSupplier) { delegate.fetch(criteria, offset, limit) }
}
