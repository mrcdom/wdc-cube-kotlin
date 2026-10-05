package br.com.wdc.shopping.persistence

import br.com.wdc.shopping.domain.product.ProductRepository
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemRepository
import br.com.wdc.shopping.domain.purchase.PurchaseRepository
import br.com.wdc.shopping.domain.user.UserRepository
import br.com.wdc.shopping.domain.security.AuthenticationService
import br.com.wdc.shopping.persistence.security.AuthenticationServiceImpl
import br.com.wdc.shopping.persistence.security.SecuredProductRepository
import br.com.wdc.shopping.persistence.security.SecuredPurchaseItemRepository
import br.com.wdc.shopping.persistence.security.SecuredPurchaseRepository
import br.com.wdc.shopping.persistence.security.SecuredUserRepository

object RepositoryBootstrap {

    fun initializeSecurity(jwtSecret: String, refreshTokenTtlDays: Int = 7) {
        val rawUserRepo = UserRepository.BEAN.get()

        UserRepository.BEAN.set(SecuredUserRepository(rawUserRepo))
        ProductRepository.BEAN.set(SecuredProductRepository(ProductRepository.BEAN.get()))
        val rawPurchaseRepo = PurchaseRepository.BEAN.get()
        PurchaseRepository.BEAN.set(SecuredPurchaseRepository(rawPurchaseRepo))
        PurchaseItemRepository.BEAN.set(SecuredPurchaseItemRepository(PurchaseItemRepository.BEAN.get(), rawPurchaseRepo))

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
