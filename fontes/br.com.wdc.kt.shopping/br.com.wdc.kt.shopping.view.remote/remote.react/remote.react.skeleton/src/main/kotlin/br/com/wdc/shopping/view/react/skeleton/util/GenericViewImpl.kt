package br.com.wdc.shopping.view.react.skeleton.util

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.shopping.view.react.skeleton.viewimpl.ReactRootPresenter

/**
 * A view of the remote React client, kept on the server: it has an instance id ("<vid>:<n>", the vid naming the
 * client component), receives the client's events and writes what the client paints.
 */
abstract class GenericViewImpl protected constructor(
    vid: String,
    protected val app: ReactRootPresenter,
    instanceId: Int = app.nextInstanceId(),
) : CubeView {

    private val _instanceId: String = "$vid:$instanceId"

    protected var alertId: Int = 0

    init {
        this.app.putView(this)
        this.app.markDirty(this)
    }

    override val instanceId: String get() = _instanceId

    override fun release() {
        app.removeView(_instanceId)
    }

    override fun update() {
        app.markDirty(this)
    }

    /** Copies what the client sent (what the user typed) before its event. */
    open fun syncClientToServer(formData: Map<String, Any?>) {
        // NOOP
    }

    @Throws(Exception::class)
    abstract suspend fun submit(eventCode: Int, eventQtde: Int, formData: Map<String, Any?>)

    abstract fun writeScope(json: ExtensibleObjectOutput)
}
