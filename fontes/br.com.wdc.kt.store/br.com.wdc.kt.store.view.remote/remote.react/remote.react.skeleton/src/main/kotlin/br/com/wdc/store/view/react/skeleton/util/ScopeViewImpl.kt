package br.com.wdc.store.view.react.skeleton.util

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.cube.Scope

/**
 * What the remote host draws a scope with: the client's view of it, known by an instance id ("<vid>:<n>", the vid
 * naming the client component that paints it). The client's events go to the actions the presenter bound with their
 * codes ([Scope.dispatch]), what the user typed to [Scope.sync], and the scope goes out as JSON with the ids of the
 * views of the scopes inside it.
 *
 * Writing a scope is drawing it, and that is where a scope that left is noticed: each view keeps the views it referred
 * to the last time it was written, and lets go of those it no longer refers to — with the views they referred to in
 * turn, unless another view still refers to them. Nothing else releases a view; the presenters do not take part.
 */
open class ScopeViewImpl<S : Scope>(
    vid: String,
    protected val app: RemoteApplication,
    val scope: S,
    instanceId: Int = app.nextInstanceId(),
) {

    val instanceId: String = "$vid:$instanceId"

    /** What the scope is told to call on a change: kept to take back only this one. */
    val updater: () -> Unit = { update() }

    /** The views this one referred to the last time it was written. */
    private var children: Set<ScopeViewImpl<*>> = emptySet()

    /** The views whose last writing referred to this one. */
    private val holders = HashSet<ScopeViewImpl<*>>()

    /** Whether this view was let go of ([release]). */
    var released: Boolean = false
        private set

    /** The scope changed: the client needs it again. */
    fun update() {
        if (!released) app.markDirty(this)
    }

    /** Copies what the client sent (what the user typed) before its event: the presenter does it ([Scope.onSync]). */
    fun syncClientToServer(formData: Map<String, Any?>) {
        scope.sync(formData)
    }

    open suspend fun submit(eventCode: Int, eventQtde: Int, formData: Map<String, Any?>) {
        scope.dispatch(eventCode, formData)
    }

    /** Writes the scope for the client; the views it no longer refers to are released. */
    open fun writeScope(json: ExtensibleObjectOutput) {
        val written = LinkedHashSet<ScopeViewImpl<*>>()
        scope.write(instanceId, json) { inner ->
            val child = app.viewOf(inner)
            child.holders += this
            written += child
            child.instanceId
        }
        val before = children
        children = written
        for (child in before) {
            if (child !in written) child.dropHolder(this)
        }
    }

    /**
     * Lets go of this view: the scope no longer tells it of changes, the client is told to drop it, and so are the
     * views it referred to that no other view refers to.
     */
    fun release() {
        if (released) return
        released = true
        scope.clearUpdater(updater)
        app.forgetView(this)
        app.removeView(instanceId)
        val gone = children
        children = emptySet()
        for (child in gone) child.dropHolder(this)
    }

    private fun dropHolder(holder: ScopeViewImpl<*>) {
        holders -= holder
        if (holders.isEmpty()) release()
    }
}
