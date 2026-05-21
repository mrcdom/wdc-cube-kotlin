package br.com.wdc.shopping.view.react.skeleton.util

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.framework.cube.PresenterBase
import br.com.wdc.shopping.view.react.skeleton.viewimpl.ApplicationReactImpl

abstract class GenericViewImpl<P : PresenterBase> protected constructor(
    vid: String,
    protected val presenter: P,
    instanceId: Int = (presenter.app as ApplicationReactImpl).nextInstanceId()
) : CubeView {

    protected val app: ApplicationReactImpl = presenter.app as ApplicationReactImpl
    private val _instanceId: String = "$vid:$instanceId"

    protected var alertId: Int = 0

    init {
        this.app.putView(this)
        this.app.markDirty(this)
    }

    fun commitComputedState() {
        presenter.commitComputedState()
    }

    override val instanceId: String get() = _instanceId

    override fun release() {
        app.removeView(_instanceId)
    }

    override fun update() {
        app.markDirty(this)
    }

    open fun syncClientToServer(formData: Map<String, Any?>) {
        // NOOP
    }

    @Throws(Exception::class)
    abstract suspend fun submit(eventCode: Int, eventQtde: Int, formData: Map<String, Any?>)

    abstract fun writeState(json: ExtensibleObjectOutput)
}
