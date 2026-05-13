package br.com.wdc.shopping.test.util

import br.com.wdc.shopping.domain.repositories.ProductRepository
import br.com.wdc.shopping.domain.repositories.PurchaseItemRepository
import br.com.wdc.shopping.domain.repositories.PurchaseRepository
import br.com.wdc.shopping.domain.repositories.UserRepository

interface ShoppingTestEnvironment {
    val userRepo: UserRepository
    val productRepo: ProductRepository
    val purchaseRepo: PurchaseRepository
    val purchaseItemRepo: PurchaseItemRepository

    fun start()
    fun stop()
    fun resetDatabase()
}
