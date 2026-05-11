package br.com.wdc.shopping.persistence.repository.purchaseitem

import br.com.wdc.framework.commons.util.TransactionContext
import br.com.wdc.shopping.domain.criteria.PurchaseItemCriteria
import br.com.wdc.shopping.domain.model.PurchaseItem
import br.com.wdc.shopping.domain.repositories.PurchaseItemRepository
import br.com.wdc.shopping.persistence.repository.BaseRepository

class PurchaseItemRepositoryImpl : BaseRepository(), PurchaseItemRepository {

    override fun insert(purchaseItem: PurchaseItem): Boolean = try {
        TransactionContext.begin(dataSource()).use { tx ->
            InsertRowPurchaseItemCmd.run(tx.connection(), purchaseItem)
        }
    } catch (e: Exception) {
        writeException(e)
    }

    override fun insertOrUpdate(purchaseItem: PurchaseItem): Boolean = try {
        TransactionContext.begin(dataSource()).use { tx ->
            if (purchaseItem.id == null) InsertRowPurchaseItemCmd.run(tx.connection(), purchaseItem)
            else UpdateRowPurchaseItemCmd.run(tx.connection(), purchaseItem)
        }
    } catch (e: Exception) {
        writeException(e)
    }

    override fun update(newPurchaseItem: PurchaseItem, oldPurchaseItem: PurchaseItem): Boolean = try {
        TransactionContext.begin(dataSource()).use { tx ->
            UpdateRowPurchaseItemCmd.run(tx.connection(), newPurchaseItem, oldPurchaseItem)
        }
    } catch (e: Exception) {
        writeException(e)
    }

    override fun delete(criteria: PurchaseItemCriteria): Int = try {
        TransactionContext.begin(dataSource()).use { tx ->
            DeletePurchaseItemsCmd.byCriteria(tx.connection(), criteria)
        }
    } catch (e: Exception) {
        writeException(e)
    }

    override fun count(criteria: PurchaseItemCriteria): Int = try {
        TransactionContext.begin(dataSource()).use { tx ->
            CountPurchaseItemsCmd.byCriteria(tx.connection(), criteria)
        }
    } catch (e: Exception) {
        writeException(e)
    }

    override fun fetch(criteria: PurchaseItemCriteria): List<PurchaseItem> = try {
        TransactionContext.begin(dataSource()).use { tx ->
            FetchPurchaseItemsCmd.byCriteria(tx.connection(), criteria)
        }
    } catch (e: Exception) {
        readException(e)
    }

    override fun fetchById(purchaseId: Long, projection: PurchaseItem?): PurchaseItem? = try {
        TransactionContext.begin(dataSource()).use { tx ->
            FetchPurchaseItemsCmd.byId(tx.connection(), purchaseId, projection)
        }
    } catch (e: Exception) {
        readException(e)
    }
}
