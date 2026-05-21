package br.com.wdc.shopping.persistence.repository.purchaseitem

import br.com.wdc.framework.commons.util.TransactionContext
import br.com.wdc.shopping.domain.criteria.PurchaseItemCriteria
import br.com.wdc.shopping.domain.model.PurchaseItem
import br.com.wdc.shopping.domain.repositories.Page
import br.com.wdc.shopping.domain.repositories.PurchaseItemRepository
import br.com.wdc.shopping.persistence.repository.BaseRepository

class PurchaseItemRepositoryImpl : BaseRepository(), PurchaseItemRepository {

    override suspend fun insert(purchaseItem: PurchaseItem): Boolean = try {
        TransactionContext.begin(dataSource()).use { tx ->
            InsertRowPurchaseItemCmd.run(tx.connection(), purchaseItem)
        }
    } catch (e: Exception) {
        writeException(e)
    }

    override suspend fun insertOrUpdate(purchaseItem: PurchaseItem): Boolean = try {
        TransactionContext.begin(dataSource()).use { tx ->
            if (purchaseItem.id == null) InsertRowPurchaseItemCmd.run(tx.connection(), purchaseItem)
            else UpdateRowPurchaseItemCmd.run(tx.connection(), purchaseItem)
        }
    } catch (e: Exception) {
        writeException(e)
    }

    override suspend fun update(newPurchaseItem: PurchaseItem, oldPurchaseItem: PurchaseItem): Boolean = try {
        TransactionContext.begin(dataSource()).use { tx ->
            UpdateRowPurchaseItemCmd.run(tx.connection(), newPurchaseItem, oldPurchaseItem)
        }
    } catch (e: Exception) {
        writeException(e)
    }

    override suspend fun delete(criteria: PurchaseItemCriteria): Int = try {
        TransactionContext.begin(dataSource()).use { tx ->
            DeletePurchaseItemsCmd.byCriteria(tx.connection(), criteria)
        }
    } catch (e: Exception) {
        writeException(e)
    }

    override suspend fun count(criteria: PurchaseItemCriteria): Int = try {
        TransactionContext.begin(dataSource()).use { tx ->
            CountPurchaseItemsCmd.byCriteria(tx.connection(), criteria)
        }
    } catch (e: Exception) {
        writeException(e)
    }

    override suspend fun fetch(criteria: PurchaseItemCriteria): List<PurchaseItem> = try {
        TransactionContext.begin(dataSource()).use { tx ->
            FetchPurchaseItemsCmd.byCriteria(tx.connection(), criteria)
        }
    } catch (e: Exception) {
        readException(e)
    }

    override suspend fun fetchPage(criteria: PurchaseItemCriteria): Page<PurchaseItem> = try {
        TransactionContext.begin(dataSource()).use { tx ->
            val totalCount = CountPurchaseItemsCmd.byCriteria(tx.connection(), criteria)
            val items = FetchPurchaseItemsCmd.byCriteria(tx.connection(), criteria)
            Page(items, totalCount)
        }
    } catch (e: Exception) {
        readException(e)
    }

    override suspend fun fetchById(purchaseId: Long, projection: PurchaseItem?): PurchaseItem? = try {
        TransactionContext.begin(dataSource()).use { tx ->
            FetchPurchaseItemsCmd.byId(tx.connection(), purchaseId, projection)
        }
    } catch (e: Exception) {
        readException(e)
    }
}
