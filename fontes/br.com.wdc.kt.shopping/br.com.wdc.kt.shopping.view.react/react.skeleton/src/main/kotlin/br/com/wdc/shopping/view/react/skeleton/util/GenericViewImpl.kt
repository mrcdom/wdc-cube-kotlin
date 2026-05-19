package br.com.wdc.shopping.view.react.skeleton.util

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.view.react.skeleton.viewimpl.ApplicationReactImpl

abstract class GenericViewImpl : CubeView {

    protected val app: ApplicationReactImpl
    private val _instanceId: String

    protected var alertId: Int = 0

    protected constructor(app: ShoppingApplication, vid: String)
        : this(app, vid, (app as ApplicationReactImpl).nextInstanceId())

    protected constructor(app: ShoppingApplication, vid: String, instanceId: Int) {
        this.app = app as ApplicationReactImpl
        this._instanceId = "$vid:$instanceId"
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

    open fun syncClientToServer(formData: Map<String, Any?>) {
        // NOOP
    }

    @Throws(Exception::class)
    abstract fun submit(eventCode: Int, eventQtde: Int, formData: Map<String, Any?>)

    abstract fun writeState(json: ExtensibleObjectOutput)
}
