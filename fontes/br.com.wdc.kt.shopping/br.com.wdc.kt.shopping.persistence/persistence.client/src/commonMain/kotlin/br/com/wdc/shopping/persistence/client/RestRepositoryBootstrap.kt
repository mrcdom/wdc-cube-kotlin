package br.com.wdc.shopping.persistence.client

import br.com.wdc.shopping.domain.ShoppingTransactions
import br.com.wdc.shopping.domain.product.ProductRepository
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemRepository
import br.com.wdc.shopping.domain.purchase.PurchaseRepository
import br.com.wdc.shopping.domain.user.UserRepository
import br.com.wdc.shopping.domain.security.AuthenticationService
import br.com.wdc.shopping.domain.security.CryptoProvider

object RestRepositoryBootstrap {

    fun initialize(config: RestConfig, cryptoProvider: CryptoProvider) {
        CryptoProvider.BEAN.set(cryptoProvider)
        UserRepository.BEAN.set(HttpUserRepository(config.transport))
        ProductRepository.BEAN.set(HttpProductRepository(config.transport))
        PurchaseRepository.BEAN.set(HttpPurchaseRepository(config.transport))
        PurchaseItemRepository.BEAN.set(HttpPurchaseItemRepository(config.transport))
        AuthenticationService.BEAN.set(RestAuthenticationService(config))
        // os casos de uso demarcam a transação no servidor, por REST
        ShoppingTransactions.BEAN.set(RestTransactionService(config.transport))
    }

    fun release() {
        ShoppingTransactions.BEAN.set(null)
        AuthenticationService.BEAN.set(null)
        UserRepository.BEAN.set(null)
        ProductRepository.BEAN.set(null)
        PurchaseRepository.BEAN.set(null)
        PurchaseItemRepository.BEAN.set(null)
    }
}
