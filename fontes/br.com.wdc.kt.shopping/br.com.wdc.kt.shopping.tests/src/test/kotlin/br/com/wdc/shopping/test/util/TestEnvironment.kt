package br.com.wdc.shopping.test.util

import br.com.wdc.framework.commons.concurrent.ScheduledExecutor
import br.com.wdc.framework.commons.sql.SqlDataSource
import br.com.wdc.framework.commons.sql.SqlDataSourceDelegate
import br.com.wdc.shopping.domain.ShoppingConfig
import br.com.wdc.shopping.domain.repositories.ProductRepository
import br.com.wdc.shopping.domain.repositories.PurchaseItemRepository
import br.com.wdc.shopping.domain.repositories.PurchaseRepository
import br.com.wdc.shopping.domain.repositories.UserRepository
import br.com.wdc.shopping.persistence.RepositoryBootstrap
import br.com.wdc.shopping.scripts.sgbd.DBCreate
import org.apache.tomcat.dbcp.dbcp.BasicDataSource
import java.nio.file.Paths

class TestEnvironment(private val dbName: String = "wedocode-shopping") : ShoppingTestEnvironment {

    private lateinit var datasource: BasicDataSource
    private lateinit var executor: ScheduledExecutorForTest

    override lateinit var userRepo: UserRepository; private set
    override lateinit var productRepo: ProductRepository; private set
    override lateinit var purchaseRepo: PurchaseRepository; private set
    override lateinit var purchaseItemRepo: PurchaseItemRepository; private set

    override fun start() {
        executor = ScheduledExecutorForTestAsync()

        val ds = BasicDataSource()
        ds.driverClassName = "org.h2.jdbcx.JdbcDataSource"
        ds.url = "jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
        ds.username = "sa"
        ds.password = "sa"
        ds.initialSize = 1
        ds.maxActive = 10
        ds.maxIdle = 5
        ds.validationQuery = "SELECT 1 FROM DUAL"
        datasource = ds

        val basePath = Paths.get("work")
        ShoppingConfig.Internals.setBaseDir(basePath)
        ShoppingConfig.Internals.setConfigDir(basePath.resolve("config"))
        ShoppingConfig.Internals.setDataDir(basePath.resolve("data"))
        ShoppingConfig.Internals.setLogDir(basePath.resolve("log"))
        ShoppingConfig.Internals.setTempDir(basePath.resolve("temp"))

        SqlDataSource.BEAN.set(SqlDataSourceDelegate(ds))
        ScheduledExecutor.BEAN.set(executor)

        RepositoryBootstrap.initialize()

        userRepo = UserRepository.BEAN.get()
        productRepo = ProductRepository.BEAN.get()
        purchaseRepo = PurchaseRepository.BEAN.get()
        purchaseItemRepo = PurchaseItemRepository.BEAN.get()
    }

    override fun stop() {
        RepositoryBootstrap.release()
        datasource.close()
        executor.shutdown()
    }

    override fun resetDatabase() {
        datasource.connection.use { connection ->
            DBCreate().withConnection(connection).withReset().run()
        }
    }
}
