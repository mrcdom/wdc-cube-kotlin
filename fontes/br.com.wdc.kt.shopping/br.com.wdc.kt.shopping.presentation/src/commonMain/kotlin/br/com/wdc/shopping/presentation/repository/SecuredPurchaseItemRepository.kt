package br.com.wdc.shopping.presentation.repository

import br.com.wdc.shopping.domain.purchaseitem.PurchaseItem
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemCriteria
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemRepository
import br.com.wdc.shopping.domain.security.SecurityContext
import br.com.wdc.shopping.presentation.util.withSecurityContext

/**
 * Executa as operações com o contexto de segurança da aplicação. `fetchById`, `fetchPage` e `insertOrUpdate`
 * vêm dos defaults da interface, que passam pelas operações abaixo.
 */
class SecuredPurchaseItemRepository(
    private val delegate: PurchaseItemRepository,
    private val contextSupplier: () -> SecurityContext?,
) : PurchaseItemRepository {

    override fun newProjection(): PurchaseItem = delegate.newProjection()

    override suspend fun insert(bean: PurchaseItem) =
        withSecurityContext(contextSupplier) { delegate.insert(bean) }

    override suspend fun update(newBean: PurchaseItem, oldBean: PurchaseItem?, projection: PurchaseItem?) =
        withSecurityContext(contextSupplier) { delegate.update(newBean, oldBean, projection) }

    override suspend fun delete(criteria: PurchaseItemCriteria) =
        withSecurityContext(contextSupplier) { delegate.delete(criteria) }

    override suspend fun count(criteria: PurchaseItemCriteria) =
        withSecurityContext(contextSupplier) { delegate.count(criteria) }

    override suspend fun fetch(criteria: PurchaseItemCriteria, offset: Int, limit: Int) =
        withSecurityContext(contextSupplier) { delegate.fetch(criteria, offset, limit) }
}
