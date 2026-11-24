package br.com.wdc.store.persistence.client

import br.com.wdc.store.domain.StoreTransactions
import br.com.wdc.store.domain.product.ProductRepository
import br.com.wdc.store.domain.purchaseitem.PurchaseItemRepository
import br.com.wdc.store.domain.purchase.PurchaseRepository
import br.com.wdc.store.domain.user.UserRepository
import br.com.wdc.store.domain.security.AuthenticationService
import br.com.wdc.store.domain.security.CryptoProvider

object RestRepositoryBootstrap {

    fun initialize(config: RestConfig, cryptoProvider: CryptoProvider) {
        CryptoProvider.BEAN.set(cryptoProvider)
        UserRepository.BEAN.set(HttpUserRepository(config.transport))
        ProductRepository.BEAN.set(HttpProductRepository(config.transport))
        PurchaseRepository.BEAN.set(HttpPurchaseRepository(config.transport))
        PurchaseItemRepository.BEAN.set(HttpPurchaseItemRepository(config.transport))
        AuthenticationService.BEAN.set(RestAuthenticationService(config))
        // use cases demarcate the transaction on the server, over REST
        StoreTransactions.BEAN.set(RestTransactionService(config.transport))
    }

    fun release() {
        StoreTransactions.BEAN.set(null)
        AuthenticationService.BEAN.set(null)
        UserRepository.BEAN.set(null)
        ProductRepository.BEAN.set(null)
        PurchaseRepository.BEAN.set(null)
        PurchaseItemRepository.BEAN.set(null)
    }
}
