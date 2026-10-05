package br.com.wdc.shopping.persistence.security

import br.com.wdc.shopping.domain.exception.AccessDeniedException
import br.com.wdc.shopping.domain.purchase.PurchaseCriteria
import br.com.wdc.shopping.domain.purchase.PurchaseRepository
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItem
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemCriteria
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemRepository
import br.com.wdc.shopping.domain.security.SecurityContext

/**
 * Aplica o controle de acesso a item de compra: permissão e escopo — quem não tem `data:all` só alcança os
 * itens das próprias compras, na leitura e na escrita, e a senha do usuário da compra nunca sai por aqui.
 *
 * `fetchById`, `fetchPage` e `insertOrUpdate` não são sobrescritos de propósito: os defaults da interface
 * passam por `fetch`, `count`, `insert` e `update`, que já são verificados.
 *
 * @param purchases repositório **sem** decoração, para conferir de quem é a compra
 */
class SecuredPurchaseItemRepository(
    private val delegate: PurchaseItemRepository,
    private val purchases: PurchaseRepository,
) : PurchaseItemRepository {

    companion object {
        private const val ENTITY = "purchase-item"
    }

    override fun newProjection(): PurchaseItem = delegate.newProjection()

    override suspend fun insert(bean: PurchaseItem): Boolean {
        val sc = SecurityEnforcer.require(ENTITY, "write")
        if (!sc.hasDataAll()) {
            requireOwnPurchase(sc, bean.purchaseId)
        }
        return delegate.insert(bean)
    }

    override suspend fun update(newBean: PurchaseItem, oldBean: PurchaseItem?, projection: PurchaseItem?): Boolean {
        val sc = SecurityEnforcer.require(ENTITY, "write")
        if (!sc.hasDataAll()) {
            val id = newBean.id
            if (id == null || delegate.count(PurchaseItemCriteria().withPurchaseItemId(id).withUserId(sc.userId)) == 0) {
                throw AccessDeniedException("Cannot modify other user's purchase item")
            }
            // o item só pode ser movido para outra compra do próprio usuário
            newBean.purchaseId?.let { requireOwnPurchase(sc, it) }
        }
        return delegate.update(newBean, oldBean, projection)
    }

    override suspend fun delete(criteria: PurchaseItemCriteria): Int {
        val sc = SecurityEnforcer.require(ENTITY, "delete")
        enforceUserScope(sc, criteria)
        return delegate.delete(criteria)
    }

    override suspend fun count(criteria: PurchaseItemCriteria): Int {
        val sc = SecurityEnforcer.require(ENTITY, "read")
        enforceUserScope(sc, criteria)
        return delegate.count(criteria)
    }

    override suspend fun fetch(criteria: PurchaseItemCriteria, offset: Int, limit: Int): List<PurchaseItem> {
        val sc = SecurityEnforcer.require(ENTITY, "read")
        enforceUserScope(sc, criteria)
        criteria.projection?.purchase?.user?.password = null
        val results = delegate.fetch(criteria, offset, limit)
        results.forEach { it.purchase?.user?.password = null }
        return results
    }

    private suspend fun requireOwnPurchase(sc: SecurityContext, purchaseId: Long?) {
        if (purchaseId == null || purchases.count(PurchaseCriteria().withPurchaseId(purchaseId).withUserId(sc.userId)) == 0) {
            throw AccessDeniedException("Cannot write items of other user's purchase")
        }
    }

    /** Os pedidos de um campo acumulam em `AND`: o que o chamador pediu continua valendo, restrito ao próprio usuário. */
    private fun enforceUserScope(sc: SecurityContext, criteria: PurchaseItemCriteria) {
        if (!sc.hasDataAll()) criteria.userId.eq(sc.userId)
    }
}
