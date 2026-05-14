package br.com.wdc.shopping.test.util

import br.com.wdc.framework.commons.concurrent.ScheduledExecutor
import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.log.Slf4jLogFactory
import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.commons.serialization.installCommon
import br.com.wdc.framework.commons.sql.SqlDataSource
import br.com.wdc.framework.commons.sql.SqlDataSourceDelegate
import br.com.wdc.shopping.domain.ShoppingConfig
import br.com.wdc.shopping.domain.repositories.ProductRepository
import br.com.wdc.shopping.domain.repositories.PurchaseItemRepository
import br.com.wdc.shopping.domain.repositories.PurchaseRepository
import br.com.wdc.shopping.domain.repositories.UserRepository
import br.com.wdc.shopping.domain.security.CryptoProvider
import br.com.wdc.shopping.domain.security.JceCryptoProvider
import br.com.wdc.shopping.persistence.RepositoryBootstrap
import br.com.wdc.shopping.persistence.client.OkHttpTransport
import br.com.wdc.shopping.persistence.client.RestConfig
import br.com.wdc.shopping.persistence.client.RestProductRepository
import br.com.wdc.shopping.persistence.client.RestPurchaseItemRepository
import br.com.wdc.shopping.persistence.client.RestPurchaseRepository
import br.com.wdc.shopping.persistence.client.RestUserRepository
import br.com.wdc.shopping.persistence.rest.RepositoryApiRoutes
import br.com.wdc.shopping.scripts.sgbd.DBCreate
import com.google.gson.Gson
import io.javalin.Javalin
import io.javalin.json.JsonMapper
import org.apache.tomcat.dbcp.dbcp.BasicDataSource
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.lang.reflect.Type
import java.nio.file.Paths

/**
 * Ambiente de teste que sobe um Javalin in-process com a camada de
 * persistência (H2 + repos JDBC) e configura os BEANs com
 * implementações REST client que comunicam via HTTP.
 *
 * Valida o round-trip completo: REST client → HTTP → Javalin →
 * repos de persistência → H2, sem segurança/auth.
 */
class RestTestEnvironment(private val dbName: String = "wedocode-shopping-rest-test") : ShoppingTestEnvironment {

    private lateinit var datasource: BasicDataSource
    private lateinit var executor: ScheduledExecutorForTest
    private lateinit var javalin: Javalin

    override lateinit var userRepo: UserRepository; private set
    override lateinit var productRepo: ProductRepository; private set
    override lateinit var purchaseRepo: PurchaseRepository; private set
    override lateinit var purchaseItemRepo: PurchaseItemRepository; private set

    var port: Int = 0
        private set

    override fun start() {
        Log.setFactory(Slf4jLogFactory())
        JsonOutputFactory.installCommon()
        JsonInputFactory.installCommon()

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

        // Inicializa repos JDBC no server-side (sem segurança)
        RepositoryBootstrap.initialize()

        // Inicia Javalin em porta aleatória
        val gson = Gson()
        val gsonMapper = object : JsonMapper {
            override fun <T : Any> fromJsonString(json: String, targetType: Type): T {
                return gson.fromJson(json, targetType)
            }
            override fun toJsonString(obj: Any, type: Type): String {
                return gson.toJson(obj, type)
            }
            override fun toJsonStream(obj: Any, type: Type): InputStream {
                return ByteArrayInputStream(toJsonString(obj, type).toByteArray())
            }
        }

        javalin = Javalin.create { config ->
            config.jsonMapper(gsonMapper)
            config.routes.exception(Exception::class.java) { e, ctx ->
                System.err.println("REST SERVER EXCEPTION on ${ctx.method()} ${ctx.path()}: ${e.message}")
                e.printStackTrace(System.err)
                ctx.status(500).json(mapOf("error" to (e.message ?: "Internal error")))
            }
            RepositoryApiRoutes.configure(config)
        }.start(0)

        port = javalin.port()

        // Cria REST client instances (NÃO substitui os BEANs — o servidor usa os JDBC repos via BEAN)
        val transport = OkHttpTransport("http://localhost:$port")
        val restConfig = RestConfig(transport)

        CryptoProvider.BEAN.set(JceCryptoProvider())
        userRepo = RestUserRepository(restConfig)
        productRepo = RestProductRepository(restConfig)
        purchaseRepo = RestPurchaseRepository(restConfig)
        purchaseItemRepo = RestPurchaseItemRepository(restConfig)
    }

    override fun stop() {
        javalin.stop()
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
