package br.com.wdc.store.domain.purchase

import br.com.wdc.framework.commons.util.AtomicRef
import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.framework.domain.repository.Repository
import br.com.wdc.store.domain.user.User

interface PurchaseRepository : Repository<Purchase, PurchaseCriteria, Long> {

    /** The purchase and the user id; items come only when asked for. */
    override fun ownFieldsProjection(): Purchase {
        val pv = ProjectionValues
        return Purchase().apply {
            id = pv.i64
            buyDate = pv.instant
            user = User().apply { id = pv.i64 }
        }
    }

    override suspend fun fetchById(id: Long, projection: Purchase?): Purchase? =
        fetch(PurchaseCriteria().withPurchaseId(id).withProjection(projection ?: ownFieldsProjection()), 0, 1).firstOrNull()

    companion object {
        val BEAN = AtomicRef<PurchaseRepository>()
    }
}
