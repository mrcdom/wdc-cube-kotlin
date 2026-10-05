package br.com.wdc.shopping.test.util

import br.com.wdc.framework.commons.concurrent.ScheduledExecutor
import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.log.Slf4jLogFactory
import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.commons.serialization.installCommon
import br.com.wdc.shopping.domain.ShoppingConfig
import br.com.wdc.shopping.domain.product.ProductRepository
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemRepository
import br.com.wdc.shopping.domain.purchase.PurchaseRepository
import br.com.wdc.shopping.domain.user.UserRepository
import br.com.wdc.shopping.domain.security.CryptoProvider
import br.com.wdc.shopping.domain.security.JceCryptoProvider
import br.com.wdc.shopping.domain.security.PasswordUtil
import br.com.wdc.framework.commons.util.Defer
import br.com.wdc.framework.persistence.transaction.RemoteTransactionCoordinatorImpl
import br.com.wdc.framework.persistence.transaction.RemoteTransactionOptions
import br.com.wdc.shopping.persistence.ShoppingRepositoryBootstrap
import br.com.wdc.shopping.persistence.rest.RemoteTransactions
import br.com.wdc.shopping.persistence.client.OkHttpTransport
import br.com.wdc.shopping.persistence.client.RestAuthClient
import br.com.wdc.shopping.persistence.client.RestConfig
import br.com.wdc.shopping.persistence.client.HttpProductRepository
import br.com.wdc.shopping.persistence.client.HttpPurchaseItemRepository
import br.com.wdc.shopping.persistence.client.HttpPurchaseRepository
import br.com.wdc.shopping.persistence.client.HttpUserRepository
import br.com.wdc.shopping.persistence.rest.RepositoryApiRoutes
import br.com.wdc.shopping.scripts.sgbd.DBCreate
import com.google.gson.Gson
import io.javalin.Javalin
import io.javalin.json.JsonMapper
import org.h2.jdbcx.JdbcConnectionPool
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
class RestTestEnvironment(
    private val dbName: String = "wedocode-shopping-rest-test",
    /** Com segredo, o servidor sobe com a segurança ligada (JWT + controle de acesso); `null` = sem segurança. */
    private val jwtSecret: String? = null,
    private val remoteTransactionOptions: RemoteTransactionOptions = RemoteTransactionOptions.defaults(),
) : ShoppingTestEnvironment {

    private lateinit var datasource: JdbcConnectionPool
    private lateinit var executor: ScheduledExecutorForTest
    private val cleanUp = Defer()
    private lateinit var javalin: Javalin

    override lateinit var userRepo: UserRepository; private set
    override lateinit var productRepo: ProductRepository; private set
    override lateinit var purchaseRepo: PurchaseRepository; private set
    override lateinit var purchaseItemRepo: PurchaseItemRepository; private set

    var port: Int = 0
        private set

    /** O transporte do cliente, para testes que falam com a API sem passar pelos repositórios. */
    lateinit var transport: OkHttpTransport; private set

    private lateinit var restConfig: RestConfig

    /** Autentica o cliente como o usuário informado (a senha da carga de demonstração é o próprio login). */
    fun loginAs(userName: String, password: String = userName) {
        val authClient = RestAuthClient(restConfig)
        restConfig.setAuthClientInstance(authClient)
        authClient.login(userName, PasswordUtil.hashPassword(password))
    }

    /** O cliente de autenticação da sessão corrente, ou `null` sem login. */
    val authClient: RestAuthClient? get() = restConfig.authClient

    /** Volta o cliente ao estado sem autenticação. */
    fun logout() {
        restConfig.setAuthClientInstance(null)
    }

    override fun start() {
        Log.setFactory(Slf4jLogFactory())
        JsonOutputFactory.installCommon()
        JsonInputFactory.installCommon()

        executor = ScheduledExecutorForTestAsync()

        val ds = JdbcConnectionPool.create("jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE", "sa", "sa")
        ds.maxConnections = 10
        datasource = ds

        val basePath = Paths.get("work")
        ShoppingConfig.Internals.setBaseDir(basePath)
        ShoppingConfig.Internals.setConfigDir(basePath.resolve("config"))
        ShoppingConfig.Internals.setDataDir(basePath.resolve("data"))
        ShoppingConfig.Internals.setLogDir(basePath.resolve("log"))
        ShoppingConfig.Internals.setTempDir(basePath.resolve("temp"))
        ScheduledExecutor.BEAN.set(executor)
        ShoppingRepositoryBootstrap.initialize(ds, cleanUp = cleanUp)
        RemoteTransactions.COORDINATOR.set(RemoteTransactionCoordinatorImpl({ ds }, remoteTransactionOptions))
        cleanUp.push { RemoteTransactions.COORDINATOR.set(null) }
        if (jwtSecret != null) {
            ShoppingRepositoryBootstrap.initializeSecurity(jwtSecret, cleanUp = cleanUp)
        }

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
        datasource.dispose()
        executor.shutdown()
    }

    override fun resetDatabase() {
        datasource.connection.use { connection ->
            DBCreate().withConnection(connection).withReset().run()
        }
    }
}
