package br.com.wdc.shopping.persistence.security

import br.com.wdc.shopping.domain.exception.AccessDeniedException
import br.com.wdc.shopping.domain.purchase.Purchase
import br.com.wdc.shopping.domain.purchase.PurchaseCriteria
import br.com.wdc.shopping.domain.purchase.PurchaseRepository
import br.com.wdc.shopping.domain.security.SecurityContext
import br.com.wdc.shopping.domain.user.User

/**
 * Aplica o controle de acesso a compra: permissão e escopo — quem não tem `data:all` só alcança as próprias
 * compras, e a senha do usuário da compra nunca sai por aqui.
 *
 * `fetchById`, `fetchPage` e `insertOrUpdate` não são sobrescritos de propósito: os defaults da interface
 * passam por `fetch`, `count`, `insert` e `update`, que já são verificados.
 */
class SecuredPurchaseRepository(private val delegate: PurchaseRepository) : PurchaseRepository {

    companion object {
        private const val ENTITY = "purchase"
    }

    override fun newProjection(): Purchase = delegate.newProjection()

    override suspend fun insert(bean: Purchase): Boolean {
        val sc = SecurityEnforcer.require(ENTITY, "write")
        if (!sc.hasDataAll()) {
            // a compra é sempre de quem a faz
            bean.user = User().apply { id = sc.userId }
        }
        return delegate.insert(bean)
    }

    override suspend fun update(newBean: Purchase, oldBean: Purchase?, projection: Purchase?): Boolean {
        val sc = SecurityEnforcer.require(ENTITY, "write")
        if (!sc.hasDataAll()) {
            val id = newBean.id
            if (id == null || delegate.count(PurchaseCriteria().withPurchaseId(id).withUserId(sc.userId)) == 0) {
                throw AccessDeniedException("Cannot modify other user's purchase")
            }
            if (newBean.user != null && newBean.userId != sc.userId) {
                throw AccessDeniedException("Cannot reassign a purchase to another user")
            }
        }
        return delegate.update(newBean, oldBean, projection)
    }

    override suspend fun delete(criteria: PurchaseCriteria): Int {
        val sc = SecurityEnforcer.require(ENTITY, "delete")
        enforceUserScope(sc, criteria)
        return delegate.delete(criteria)
    }

    override suspend fun count(criteria: PurchaseCriteria): Int {
        val sc = SecurityEnforcer.require(ENTITY, "read")
        enforceUserScope(sc, criteria)
        return delegate.count(criteria)
    }

    override suspend fun fetch(criteria: PurchaseCriteria, offset: Int, limit: Int): List<Purchase> {
        val sc = SecurityEnforcer.require(ENTITY, "read")
        enforceUserScope(sc, criteria)
        criteria.projection?.user?.password = null
        val results = delegate.fetch(criteria, offset, limit)
        results.forEach { it.user?.password = null }
        return results
    }

    /** Os pedidos de um campo acumulam em `AND`: o que o chamador pediu continua valendo, restrito ao próprio usuário. */
    private fun enforceUserScope(sc: SecurityContext, criteria: PurchaseCriteria) {
        if (!sc.hasDataAll()) criteria.userId.eq(sc.userId)
    }
}
