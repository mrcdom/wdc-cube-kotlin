package br.com.wdc.shopping.test.util

import br.com.wdc.framework.commons.concurrent.ScheduledExecutor
import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.commons.serialization.installCommon
import br.com.wdc.shopping.domain.ShoppingConfig
import br.com.wdc.shopping.domain.product.ProductRepository
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemRepository
import br.com.wdc.shopping.domain.purchase.PurchaseRepository
import br.com.wdc.shopping.domain.user.UserRepository
import br.com.wdc.framework.commons.util.Defer
import br.com.wdc.shopping.domain.security.CryptoProvider
import br.com.wdc.shopping.domain.security.JceCryptoProvider
import br.com.wdc.shopping.persistence.ShoppingRepositoryBootstrap
import br.com.wdc.shopping.scripts.sgbd.DBCreate
import java.nio.file.Paths

class TestEnvironment(
    private val dbName: String = "wedocode-shopping",
    /** Com segredo, a segurança fica ligada (serviço de autenticação + controle de acesso); `null` = sem segurança. */
    private val jwtSecret: String? = null,
) : ShoppingTestEnvironment {

    private lateinit var database: TestDatabase
    private lateinit var executor: ScheduledExecutorForTest
    private val cleanUp = Defer()

    override lateinit var userRepo: UserRepository; private set
    override lateinit var productRepo: ProductRepository; private set
    override lateinit var purchaseRepo: PurchaseRepository; private set
    override lateinit var purchaseItemRepo: PurchaseItemRepository; private set

    override fun start() {
        executor = ScheduledExecutorForTestAsync()

        JsonInputFactory.installCommon()
        JsonOutputFactory.installCommon()

        database = TestDatabase.open(dbName)
        val ds = database.dataSource

        val basePath = Paths.get("work")
        ShoppingConfig.Internals.setBaseDir(basePath)
        ShoppingConfig.Internals.setConfigDir(basePath.resolve("config"))
        ShoppingConfig.Internals.setDataDir(basePath.resolve("data"))
        ShoppingConfig.Internals.setLogDir(basePath.resolve("log"))
        ShoppingConfig.Internals.setTempDir(basePath.resolve("temp"))
        ScheduledExecutor.BEAN.set(executor)
        // o login sem serviço de autenticação confere o resumo da senha na apresentação
        CryptoProvider.BEAN.set(JceCryptoProvider())
        ShoppingRepositoryBootstrap.initialize(ds, dialect = database.dialect, cleanUp = cleanUp)
        if (jwtSecret != null) {
            ShoppingRepositoryBootstrap.initializeSecurity(jwtSecret, cleanUp = cleanUp)
        }

        userRepo = UserRepository.BEAN.get()
        productRepo = ProductRepository.BEAN.get()
        purchaseRepo = PurchaseRepository.BEAN.get()
        purchaseItemRepo = PurchaseItemRepository.BEAN.get()
    }

    override fun stop() {
        cleanUp.run()
        database.close()
        executor.shutdown()
    }

    override fun resetDatabase() {
        database.dataSource.connection.use { connection ->
            DBCreate().withConnection(connection).withReset().run()
        }
    }
}
