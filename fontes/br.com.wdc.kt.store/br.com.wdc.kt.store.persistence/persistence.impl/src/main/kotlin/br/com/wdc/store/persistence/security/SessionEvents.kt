package br.com.wdc.store.persistence.security

/**
 * How the backend instances tell each other that a session ended — signed out, revoked, or replaced by a renewal —
 * so that none keeps accepting it from its memory. Each instance keeps the sessions it has read ([AccessContextCache]);
 * without this, one ended on another instance stays valid here until its access token expires.
 *
 * [LOCAL] tells no one: right for one instance. [PostgresSessionEvents] tells every instance on the same PostgreSQL
 * database; the backend's mesh between instances (`StoreCluster`, with `cluster.peers`) tells the instances it
 * connects, on any database.
 */
interface SessionEvents {

    /**
     * Tells the other instances that [sessionId] ended; called once its end is stored. PostgreSQL's `NOTIFY` goes out
     * when the current transaction commits, if there is one; the mesh sends at once.
     */
    fun sessionEnded(sessionId: String)

    /**
     * What to do when another instance ends a session ([onEnded]), and when some notices may have been missed — the
     * connection that listens was lost — so that nothing kept in memory can be trusted ([onMissed]).
     */
    fun listen(onEnded: (sessionId: String) -> Unit, onMissed: () -> Unit)

    companion object {
        /** One instance: there is no one to tell. */
        val LOCAL: SessionEvents = object : SessionEvents {
            override fun sessionEnded(sessionId: String) {}

            override fun listen(onEnded: (sessionId: String) -> Unit, onMissed: () -> Unit) {}
        }
    }
}
