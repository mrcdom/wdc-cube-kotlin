package br.com.wdc.store.persistence.security

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.store.persistence.StoreDSLContext
import org.postgresql.PGConnection
import java.sql.Connection
import javax.sql.DataSource

/**
 * [SessionEvents] over PostgreSQL's `LISTEN`/`NOTIFY`, on the database every instance shares.
 *
 * Ending a session sends `pg_notify(CHANNEL, sessionId)` through the store's connection — inside a transaction, it goes
 * when the transaction commits. Each instance listens on a connection of its own, from a thread of its own
 * ([start]), and tells its session cache. When that connection is lost, the notices sent meanwhile are lost with it:
 * after reconnecting, the cache is told to trust nothing it kept ([SessionEvents.listen]'s `onMissed`), and reads the
 * sessions from the database again.
 */
class PostgresSessionEvents(private val dataSource: DataSource) : SessionEvents, AutoCloseable {

    companion object {
        private val LOG = Log.getLogger("PostgresSessionEvents")

        /** The channel the instances talk on. */
        const val CHANNEL = "wdc_store_session_ended"

        private const val POLL_MILLIS = 500
        private const val RETRY_MILLIS = 2_000L
    }

    @Volatile
    private var onEnded: (String) -> Unit = {}

    @Volatile
    private var onMissed: () -> Unit = {}

    @Volatile
    private var running = false
    private var thread: Thread? = null

    override fun sessionEnded(sessionId: String) {
        StoreDSLContext.BEAN.get().execute("select pg_notify(?, ?)", CHANNEL, sessionId)
    }

    override fun listen(onEnded: (sessionId: String) -> Unit, onMissed: () -> Unit) {
        this.onEnded = onEnded
        this.onMissed = onMissed
    }

    /** Starts listening, in a thread of its own; [close] stops it. */
    fun start(): PostgresSessionEvents {
        running = true
        thread = Thread.ofVirtual().name("session-events").start { listenLoop() }
        return this
    }

    override fun close() {
        running = false
        thread?.interrupt()
        thread?.join(2_000)
    }

    private fun listenLoop() {
        var connectedBefore = false
        while (running) {
            try {
                dataSource.connection.use { connection ->
                    connection.autoCommit = true
                    connection.createStatement().use { it.execute("LISTEN $CHANNEL") }
                    // a reconnection may have missed notices: nothing kept in memory can be trusted
                    if (connectedBefore) onMissed()
                    connectedBefore = true
                    poll(connection)
                }
            } catch (e: Exception) {
                if (!running) return
                LOG.warn("Listening to session events failed ({}); retrying", e.message)
                try {
                    Thread.sleep(RETRY_MILLIS)
                } catch (_: InterruptedException) {
                    return
                }
            }
        }
    }

    private fun poll(connection: Connection) {
        val pg = connection.unwrap(PGConnection::class.java)
        while (running) {
            val notifications = pg.getNotifications(POLL_MILLIS) ?: continue
            for (notification in notifications) {
                if (notification.name == CHANNEL) onEnded(notification.parameter)
            }
        }
    }
}
