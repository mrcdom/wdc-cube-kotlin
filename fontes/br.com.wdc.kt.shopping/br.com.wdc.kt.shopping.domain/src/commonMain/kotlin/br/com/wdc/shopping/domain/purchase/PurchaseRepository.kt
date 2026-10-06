package br.com.wdc.shopping.domain.purchase

import br.com.wdc.framework.commons.util.AtomicRef
import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.framework.domain.repository.Repository
import br.com.wdc.shopping.domain.user.User

interface PurchaseRepository : Repository<Purchase, PurchaseCriteria, Long> {

    /** A compra e o id do usuário; os itens vêm só quando pedidos. */
    override fun newProjection(): Purchase {
        val pv = ProjectionValues
        return Purchase().apply {
            id = pv.i64
            buyDate = pv.instant
            user = User().apply { id = pv.i64 }
        }
    }

    override suspend fun fetchById(id: Long, projection: Purchase?): Purchase? =
        fetch(PurchaseCriteria().withPurchaseId(id).withProjection(projection ?: newProjection()), 0, 1).firstOrNull()

    companion object {
        val BEAN = AtomicRef<PurchaseRepository>()
    }
}
