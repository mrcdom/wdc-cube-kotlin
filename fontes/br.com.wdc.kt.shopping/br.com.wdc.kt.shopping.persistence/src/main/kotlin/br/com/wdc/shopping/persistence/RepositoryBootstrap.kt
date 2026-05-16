package br.com.wdc.shopping.persistence

import br.com.wdc.shopping.domain.repositories.ProductRepository
import br.com.wdc.shopping.domain.repositories.PurchaseItemRepository
import br.com.wdc.shopping.domain.repositories.PurchaseRepository
import br.com.wdc.shopping.domain.repositories.UserRepository
import br.com.wdc.shopping.domain.security.AuthenticationService
import br.com.wdc.shopping.persistence.repository.product.ProductRepositoryImpl
import br.com.wdc.shopping.persistence.repository.purchase.PurchaseRepositoryImpl
import br.com.wdc.shopping.persistence.repository.purchaseitem.PurchaseItemRepositoryImpl
import br.com.wdc.shopping.persistence.repository.user.UserRepositoryImpl
import br.com.wdc.shopping.persistence.security.AuthenticationServiceImpl
import br.com.wdc.shopping.persistence.security.SecuredProductRepository
import br.com.wdc.shopping.persistence.security.SecuredPurchaseItemRepository
import br.com.wdc.shopping.persistence.security.SecuredPurchaseRepository
import br.com.wdc.shopping.persistence.security.SecuredUserRepository

object RepositoryBootstrap {

    fun initialize() {
        UserRepository.BEAN.set(UserRepositoryImpl())
        ProductRepository.BEAN.set(ProductRepositoryImpl())
        PurchaseRepository.BEAN.set(PurchaseRepositoryImpl())
        PurchaseItemRepository.BEAN.set(PurchaseItemRepositoryImpl())
    }

    fun initializeSecurity(jwtSecret: String, refreshTokenTtlDays: Int = 7) {
        val rawUserRepo = UserRepository.BEAN.get()

        UserRepository.BEAN.set(SecuredUserRepository(rawUserRepo))
        ProductRepository.BEAN.set(SecuredProductRepository(ProductRepository.BEAN.get()))
        PurchaseRepository.BEAN.set(SecuredPurchaseRepository(PurchaseRepository.BEAN.get()))
        PurchaseItemRepository.BEAN.set(SecuredPurchaseItemRepository(PurchaseItemRepository.BEAN.get()))

        AuthenticationService.BEAN.set(AuthenticationServiceImpl(rawUserRepo, jwtSecret, refreshTokenTtlDays))
    }

    fun release() {
        AuthenticationService.BEAN.set(null)
        UserRepository.BEAN.set(null)
        ProductRepository.BEAN.set(null)
        PurchaseRepository.BEAN.set(null)
        PurchaseItemRepository.BEAN.set(null)
    }
}
