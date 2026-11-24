package br.com.wdc.store.test.util

import br.com.wdc.framework.commons.concurrent.ScheduledExecutor
import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.commons.serialization.installCommon
import br.com.wdc.store.domain.StoreConfig
import br.com.wdc.store.domain.product.ProductRepository
import br.com.wdc.store.domain.purchaseitem.PurchaseItemRepository
import br.com.wdc.store.domain.purchase.PurchaseRepository
import br.com.wdc.store.domain.user.UserRepository
import br.com.wdc.framework.commons.util.Defer
import br.com.wdc.store.domain.security.CryptoProvider
import br.com.wdc.store.domain.security.JceCryptoProvider
import br.com.wdc.store.persistence.StoreRepositoryBootstrap
import br.com.wdc.store.scripts.sgbd.DBCreate
import java.nio.file.Paths

class TestEnvironment(
    private val dbName: String = "wedocode-store",
    /** With a secret, security is on (authentication service + access control); `null` = no security. */
    private val jwtSecret: String? = null,
) : StoreTestEnvironment {

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
        StoreConfig.Internals.setWorkDir(basePath)
        StoreConfig.Internals.setConfigDir(basePath.resolve("config"))
        StoreConfig.Internals.setDataDir(basePath.resolve("data"))
        StoreConfig.Internals.setLogDir(basePath.resolve("log"))
        StoreConfig.Internals.setTmpDir(basePath.resolve("tmp"))
        ScheduledExecutor.BEAN.set(executor)
        // login without an authentication service checks the password digest in the presentation
        CryptoProvider.BEAN.set(JceCryptoProvider())
        StoreRepositoryBootstrap.initialize(ds, dialect = database.dialect, cleanUp = cleanUp)
        if (jwtSecret != null) {
            StoreRepositoryBootstrap.initializeSecurity(jwtSecret, cleanUp = cleanUp)
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
