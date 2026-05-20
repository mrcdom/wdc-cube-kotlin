package br.com.wdc.shopping.view.react.skeleton.util

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.cube.CubePresenter
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.view.react.skeleton.viewimpl.ApplicationReactImpl

abstract class GenericViewImpl protected constructor(
    app: ShoppingApplication,
    vid: String,
    private val presenter: CubePresenter? = null,
    instanceId: Int = (app as ApplicationReactImpl).nextInstanceId()
) : CubeView {

    protected val app: ApplicationReactImpl = app as ApplicationReactImpl
    private val _instanceId: String = "$vid:$instanceId"

    protected var alertId: Int = 0

    init {
        this.app.putView(this)
        this.app.markDirty(this)
    }

    fun commitComputedState() {
        presenter?.commitComputedState()
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
    abstract fun submit(eventCode: Int, eventQtde: Int, formData: Map<String, Any?>)

    abstract fun writeState(json: ExtensibleObjectOutput)
}
