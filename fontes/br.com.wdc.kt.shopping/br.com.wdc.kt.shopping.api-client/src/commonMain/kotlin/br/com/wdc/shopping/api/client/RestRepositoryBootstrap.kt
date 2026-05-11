package br.com.wdc.shopping.api.client

import br.com.wdc.shopping.domain.repositories.ProductRepository
import br.com.wdc.shopping.domain.repositories.PurchaseItemRepository
import br.com.wdc.shopping.domain.repositories.PurchaseRepository
import br.com.wdc.shopping.domain.repositories.UserRepository
import br.com.wdc.shopping.domain.security.AuthenticationService
import br.com.wdc.shopping.domain.security.CryptoProvider

object RestRepositoryBootstrap {

    fun initialize(config: RestConfig, cryptoProvider: CryptoProvider) {
        CryptoProvider.BEAN.set(cryptoProvider)
        UserRepository.BEAN.set(RestUserRepository(config))
        ProductRepository.BEAN.set(RestProductRepository(config))
        PurchaseRepository.BEAN.set(RestPurchaseRepository(config))
        PurchaseItemRepository.BEAN.set(RestPurchaseItemRepository(config))
        AuthenticationService.BEAN.set(RestAuthenticationService(config))
    }

    fun release() {
        AuthenticationService.BEAN.set(null)
        UserRepository.BEAN.set(null)
        ProductRepository.BEAN.set(null)
        PurchaseRepository.BEAN.set(null)
        PurchaseItemRepository.BEAN.set(null)
    }
}
