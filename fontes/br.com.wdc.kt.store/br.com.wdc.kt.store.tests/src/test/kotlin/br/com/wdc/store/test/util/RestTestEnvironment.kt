package br.com.wdc.store.test.util

import br.com.wdc.framework.commons.concurrent.ScheduledExecutor
import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.log.Slf4jLogFactory
import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.commons.serialization.installCommon
import br.com.wdc.store.domain.StoreConfig
import br.com.wdc.store.domain.product.ProductRepository
import br.com.wdc.store.domain.purchaseitem.PurchaseItemRepository
import br.com.wdc.store.domain.purchase.PurchaseRepository
import br.com.wdc.store.domain.user.UserRepository
import br.com.wdc.store.domain.security.CryptoProvider
import br.com.wdc.store.domain.security.JceCryptoProvider
import br.com.wdc.store.domain.security.PasswordUtil
import br.com.wdc.framework.commons.util.Defer
import br.com.wdc.framework.persistence.transaction.RemoteTransactionCoordinatorImpl
import br.com.wdc.framework.persistence.transaction.RemoteTransactionOptions
import br.com.wdc.store.persistence.StoreRepositoryBootstrap
import br.com.wdc.store.persistence.rest.RemoteTransactions
import br.com.wdc.store.persistence.client.OkHttpTransport
import br.com.wdc.store.persistence.client.RestAuthClient
import br.com.wdc.store.persistence.client.RestConfig
import br.com.wdc.store.persistence.client.HttpProductRepository
import br.com.wdc.store.persistence.client.HttpPurchaseItemRepository
import br.com.wdc.store.persistence.client.HttpPurchaseRepository
import br.com.wdc.store.persistence.client.HttpUserRepository
import br.com.wdc.store.persistence.rest.RepositoryApiRoutes
import br.com.wdc.store.scripts.sgbd.DBCreate
import com.google.gson.Gson
import io.javalin.Javalin
import io.javalin.json.JsonMapper
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.lang.reflect.Type
import java.nio.file.Paths

/**
 * Test environment that starts an in-process Javalin with the
 * persistence layer (H2 + JDBC repos) and configures the BEANs with
 * REST client implementations that communicate over HTTP.
 *
 * Validates the full round trip: REST client → HTTP → Javalin →
 * persistence repos → H2, without security/auth.
 */
class RestTestEnvironment(
    private val dbName: String = "wedocode-store-rest-test",
    /** With a secret, the server starts with security on (JWT + access control); `null` = no security. */
    private val jwtSecret: String? = null,
    private val remoteTransactionOptions: RemoteTransactionOptions = RemoteTransactionOptions.defaults(),
) : StoreTestEnvironment {

    private lateinit var database: TestDatabase
    private lateinit var executor: ScheduledExecutorForTest
    private val cleanUp = Defer()
    private lateinit var javalin: Javalin

    override lateinit var userRepo: UserRepository; private set
    override lateinit var productRepo: ProductRepository; private set
    override lateinit var purchaseRepo: PurchaseRepository; private set
    override lateinit var purchaseItemRepo: PurchaseItemRepository; private set

    var port: Int = 0
        private set

    /** The client transport, for tests that talk to the API without going through the repositories. */
    lateinit var transport: OkHttpTransport; private set

    private lateinit var restConfig: RestConfig

    /** Authenticates the client as the given user (the demo seed password is the login itself). */
    fun loginAs(userName: String, password: String = userName) {
        val authClient = RestAuthClient(restConfig)
        restConfig.setAuthClientInstance(authClient)
        authClient.login(userName, PasswordUtil.hashPassword(password))
    }

    /** The authentication client of the current session, or `null` without login. */
    val authClient: RestAuthClient? get() = restConfig.authClient

    /** Returns the client to the unauthenticated state. */
    fun logout() {
        restConfig.setAuthClientInstance(null)
    }

    override fun start() {
        Log.setFactory(Slf4jLogFactory())
        JsonOutputFactory.installCommon()
        JsonInputFactory.installCommon()

        executor = ScheduledExecutorForTestAsync()

        database = TestDatabase.open(dbName)
        val ds = database.dataSource

        val basePath = Paths.get("work")
        StoreConfig.Internals.setWorkDir(basePath)
        StoreConfig.Internals.setConfigDir(basePath.resolve("config"))
        StoreConfig.Internals.setDataDir(basePath.resolve("data"))
        StoreConfig.Internals.setLogDir(basePath.resolve("log"))
        StoreConfig.Internals.setTmpDir(basePath.resolve("tmp"))
        ScheduledExecutor.BEAN.set(executor)
        StoreRepositoryBootstrap.initialize(ds, dialect = database.dialect, cleanUp = cleanUp)
        RemoteTransactions.COORDINATOR.set(RemoteTransactionCoordinatorImpl({ ds }, remoteTransactionOptions))
        cleanUp.push { RemoteTransactions.COORDINATOR.set(null) }
        if (jwtSecret != null) {
            StoreRepositoryBootstrap.initializeSecurity(jwtSecret, cleanUp = cleanUp)
        }

        // Starts Javalin on a random port
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
        }.start("127.0.0.1", 0)

        port = javalin.port()

        // Creates REST client instances (does NOT replace the BEANs — the server uses the JDBC repos through BEAN)
        val transport = OkHttpTransport("http://127.0.0.1:$port")
        val restConfig = RestConfig(transport)
        this.transport = transport
        this.restConfig = restConfig

        CryptoProvider.BEAN.set(JceCryptoProvider())
        userRepo = HttpUserRepository(transport)
        productRepo = HttpProductRepository(transport)
        purchaseRepo = HttpPurchaseRepository(transport)
        purchaseItemRepo = HttpPurchaseItemRepository(transport)
    }

    override fun stop() {
        javalin.stop()
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
