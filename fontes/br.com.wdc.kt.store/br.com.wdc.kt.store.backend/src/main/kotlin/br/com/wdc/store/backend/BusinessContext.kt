package br.com.wdc.store.backend

import br.com.wdc.framework.commons.concurrent.ScheduledExecutor
import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.util.Defer
import br.com.wdc.framework.cube.remote.RemoteAppSecurity
import br.com.wdc.framework.persistence.transaction.RemoteTransactionCoordinatorImpl
import br.com.wdc.framework.persistence.transaction.RemoteTransactionOptions
import br.com.wdc.store.backend.cluster.ClusterKey
import br.com.wdc.store.backend.cluster.ClusterMesh
import br.com.wdc.store.backend.cluster.StoreCluster
import br.com.wdc.store.backend.supports.SqlDataSourceSupport
import br.com.wdc.store.domain.StoreConfig
import br.com.wdc.store.domain.config.AppConfig
import br.com.wdc.store.domain.security.AuthenticationService
import br.com.wdc.store.domain.security.CryptoProvider
import br.com.wdc.store.domain.security.JceCryptoProvider
import br.com.wdc.store.persistence.StoreRepositoryBootstrap
import br.com.wdc.store.persistence.concurrent.ScheduledExecutorAdapter
import br.com.wdc.store.persistence.rest.RemoteTransactions
import br.com.wdc.store.persistence.security.AuthenticationServiceImpl
import br.com.wdc.store.scripts.sgbd.DBCreate
import br.com.wdc.store.view.remote.RemoteRootPresenter
import br.com.wdc.store.view.remote.VirtualThreadFactory
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

/**
 * @param config the service configuration, already loaded from the work directory
 */
class BusinessContext(private val config: AppConfig) {

    companion object {
        private val LOG = Log.getLogger("BusinessContext")
    }

    private val cleanUp = Defer()

    /** The other instances to tell what they must know (`cluster.peers`); `null` without them. */
    var cluster: StoreCluster? = null
        private set

    /** The addresses of `cluster.peers`, connected to once the server is up ([startCluster]). */
    private val peers: List<String> = config.getList("cluster.peers")

    fun stop() {
        cluster = null
        cleanUp.run()
        ScheduledExecutor.BEAN.set(null)
        CryptoProvider.BEAN.set(null)
    }

    fun start() {
        try {
            CryptoProvider.BEAN.set(JceCryptoProvider())

            val scheduledExecutor = createScheduledExecutor()
            ScheduledExecutor.BEAN.set(ScheduledExecutorAdapter(scheduledExecutor))

            val dataSourceSupport = SqlDataSourceSupport(config, StoreConfig.dataDir)
            val dataSource = dataSourceSupport.init(cleanUp)

            dataSource.connection.use { connection ->
                val command = DBCreate().withConnection(connection)
                if (config.getBoolean("database.reset", false)) {
                    command.withReset()
                }
                command.run()
            }

            StoreRepositoryBootstrap.initialize(dataSource, dataSourceSupport.logSql, dataSourceSupport.dialect, cleanUp)

            // the transactions the REST clients open and close on their own
            val coordinator = RemoteTransactionCoordinatorImpl({ dataSource }, RemoteTransactionOptions.fromConfig("", config::getInt))
            RemoteTransactions.COORDINATOR.set(coordinator)
            cleanUp.push { RemoteTransactions.COORDINATOR.set(null) }
            // rolls back abandoned transactions even when no one opens a new one
            val reaper = coordinator.startReaper(scheduledExecutor)
            cleanUp.push { reaper.close() }
            // releases the remote React applications whose client went away
            val remoteApps = RemoteRootPresenter.REGISTRY.startCleanup(scheduledExecutor)
            cleanUp.push { remoteApps.close() }

            val jwtSecret = StoreConfig.jwtSecret
            val cluster = createCluster(jwtSecret)
            if (!jwtSecret.isNullOrBlank()) {
                // the remote view's session ids are signed with a key every instance of this configuration shares
                RemoteAppSecurity.useKey(RemoteSigningKey.from(jwtSecret))
                StoreRepositoryBootstrap.initializeSecurity(
                    jwtSecret = jwtSecret,
                    refreshTokenTtlDays = StoreConfig.refreshTokenTtlDays,
                    cleanUp = cleanUp,
                    events = cluster?.sessionEvents,
                )
                // drops the expired sessions and the unanswered login challenges, even when no one signs in
                (AuthenticationService.BEAN.getOrNull() as? AuthenticationServiceImpl)?.let { auth ->
                    val cleanup = auth.startCleanup(scheduledExecutor)
                    cleanUp.push { cleanup.close() }
                }
            }

            if (jwtSecret.isNullOrBlank()) {
                LOG.info("Remote session ids are signed with a key of this process: one instance only (security.jwt.secret shares one)")
            }

            LOG.info("WDC Store backend context initialized with database {}", dataSourceSupport.jdbcUrl)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to initialize the store backend context", e)
        }
    }

    /**
     * The mesh to the other instances, when `cluster.peers` names them. Their proof of being one of them is a key
     * derived from the JWT secret, so the mesh needs it.
     */
    private fun createCluster(jwtSecret: String?): StoreCluster? {
        if (peers.isEmpty()) return null
        check(!jwtSecret.isNullOrBlank()) {
            "cluster.peers needs security.jwt.secret: the instances prove to each other a key derived from it"
        }
        val mesh = ClusterMesh(ClusterKey.from(jwtSecret))
        cleanUp.push { mesh.close() }
        val cluster = StoreCluster(mesh)
        val images = cluster.shareImageChanges()
        cleanUp.push { images.close() }
        val remoteSessions = cluster.shareRemoteSessions(RemoteRootPresenter.REGISTRY)
        cleanUp.push { remoteSessions.close() }
        this.cluster = cluster
        return cluster
    }

    /** Connects to the other instances: once this one is up, so that their connections to it find it. */
    fun startCluster() {
        cluster?.mesh?.start(peers)
    }

    private fun createScheduledExecutor(): ScheduledExecutorService {
        return Executors.newScheduledThreadPool(1, VirtualThreadFactory.ofVirtual("ScheduledTasks"))
    }
}
