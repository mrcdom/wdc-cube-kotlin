package br.com.wdc.store.test.util

import br.com.wdc.store.domain.product.ProductRepository
import br.com.wdc.store.domain.purchaseitem.PurchaseItemRepository
import br.com.wdc.store.domain.purchase.PurchaseRepository
import br.com.wdc.store.domain.user.UserRepository

interface StoreTestEnvironment {
    val userRepo: UserRepository
    val productRepo: ProductRepository
    val purchaseRepo: PurchaseRepository
    val purchaseItemRepo: PurchaseItemRepository

    fun start()
    fun stop()
    fun resetDatabase()
}
