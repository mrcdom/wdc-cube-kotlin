package br.com.wdc.shopping.persistence.repository.purchase

import br.com.wdc.framework.commons.util.TransactionContext
import br.com.wdc.shopping.domain.criteria.PurchaseCriteria
import br.com.wdc.shopping.domain.model.Purchase
import br.com.wdc.shopping.domain.repositories.Page
import br.com.wdc.shopping.domain.repositories.PurchaseRepository
import br.com.wdc.shopping.persistence.repository.BaseRepository

class PurchaseRepositoryImpl : BaseRepository(), PurchaseRepository {

    override fun insert(purchase: Purchase): Boolean = try {
        TransactionContext.begin(dataSource()).use { tx ->
            InsertRowPurchaseCmd.runWithItems(tx.connection(), purchase)
        }
    } catch (e: Exception) {
        writeException(e)
    }

    override fun insertOrUpdate(purchase: Purchase): Boolean = try {
        TransactionContext.begin(dataSource()).use { tx ->
            if (purchase.id == null) InsertRowPurchaseCmd.runWithItems(tx.connection(), purchase)
            else UpdateRowPurchaseCmd.run(tx.connection(), purchase)
        }
    } catch (e: Exception) {
        writeException(e)
    }

    override fun update(newPurchase: Purchase, oldPurchase: Purchase): Boolean = try {
        TransactionContext.begin(dataSource()).use { tx ->
            UpdateRowPurchaseCmd.run(tx.connection(), newPurchase, oldPurchase)
        }
    } catch (e: Exception) {
        writeException(e)
    }

    override fun delete(criteria: PurchaseCriteria): Int = try {
        TransactionContext.begin(dataSource()).use { tx ->
            DeletePurchasesCmd.byCriteria(tx.connection(), criteria)
        }
    } catch (e: Exception) {
        readException(e)
    }

    override fun count(criteria: PurchaseCriteria): Int = try {
        TransactionContext.begin(dataSource()).use { tx ->
            CountPurchasesCmd.byCriteria(tx.connection(), criteria)
        }
    } catch (e: Exception) {
        readException(e)
    }

    override fun fetch(criteria: PurchaseCriteria): List<Purchase> = try {
        TransactionContext.begin(dataSource()).use { tx ->
            FetchPurchaseCmd.byCriteria(tx.connection(), criteria)
        }
    } catch (e: Exception) {
        readException(e)
    }

    override fun fetchPage(criteria: PurchaseCriteria): Page<Purchase> = try {
        TransactionContext.begin(dataSource()).use { tx ->
            val totalCount = CountPurchasesCmd.byCriteria(tx.connection(), criteria)
            val items = FetchPurchaseCmd.byCriteria(tx.connection(), criteria)
            Page(items, totalCount)
        }
    } catch (e: Exception) {
        readException(e)
    }

    override fun fetchById(purchaseId: Long, projection: Purchase?): Purchase? = try {
        TransactionContext.begin(dataSource()).use { tx ->
            FetchPurchaseCmd.byId(tx.connection(), purchaseId, projection)
        }
    } catch (e: Exception) {
        readException(e)
    }
}
