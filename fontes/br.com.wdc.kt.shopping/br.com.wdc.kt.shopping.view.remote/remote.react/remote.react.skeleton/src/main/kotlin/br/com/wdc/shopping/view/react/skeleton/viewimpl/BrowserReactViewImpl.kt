package br.com.wdc.shopping.view.react.skeleton.viewimpl

import br.com.wdc.framework.commons.function.Registration
import br.com.wdc.framework.commons.lang.CoerceUtils
import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.shopping.view.react.skeleton.util.GenericViewImpl

class BrowserReactViewImpl(app: ApplicationReactImpl) : GenericViewImpl(app, VID, instanceId = 0) {

    companion object {
        const val VID = "7b32e816a191"
        const val VSID = "$VID:0"
    }

    private var alertArgs: List<String> = emptyList()
    private var alertAction: () -> Unit = {}

    fun alertUnexpectedError(msg: String?, cause: Throwable?) {
        var message = msg
        val alertCode = -1
        if (message == null) {
            message = cause?.message ?: "Ocorreu um erro não previsto"
        }

        if (cause != null) {
            val detail = cause.stackTraceToString()
            alert({}, alertCode, message, detail)
        } else {
            alert({}, alertCode, message)
        }
    }

    fun alert(action: () -> Unit, code: Int, vararg args: Any?) {
        val oldAlertAction = this.alertAction
        this.alertAction = {
            oldAlertAction()
            action()
        }

        this.alertId = code
        this.alertArgs = emptyList()

        if (args.isNotEmpty()) {
            this.alertArgs = args.map { it.toString() }
        }
        update()
    }

    @Throws(Exception::class)
    private fun onStart(path: String?) {
        app.safeGo(path)
        app.updateAllViews()
    }

    private fun onHistoryChanged(path: String?) {
        try {
            app.safeGo(path)
        } catch (e: Exception) {
            val logger = Log.getLogger("this")
            logger.warn("onHistoryChanged", e)
        }
    }

    private fun onAlertOk(): Boolean {
        try {
            this.alertId = 0
            this.alertArgs = emptyList()
            this.alertAction()
        } finally {
            this.alertAction = {}
            update()
        }
        return true
    }

    private fun onKeepAlive(): Boolean {
        app.extendLife()
        return true
    }

    @Throws(Exception::class)
    override fun submit(eventCode: Int, eventQtde: Int, formData: Map<String, Any?>) {
        when (eventCode) {
            1 -> onAlertOk()
            2 -> onKeepAlive()
            -1 -> {
                try {
                    val path = CoerceUtils.asString(formData["p.path"])
                    onStart(path)
                } catch (e: Exception) {
                    val logger = Log.getLogger("this")
                    logger.warn("onStart", e)
                }
            }
            -2 -> {
                val path = CoerceUtils.asString(formData["p.path"])
                onHistoryChanged(path)
            }
        }
    }

    override fun writeState(json: ExtensibleObjectOutput) {
        json.beginObject()
        run {
            json.name("id").value(instanceId)

            if (alertId != 0) {
                json.name("alertMessage").beginObject()
                run {
                    json.name("id").value(alertId)
                    json.name("args").beginArray()
                    run {
                        alertArgs.forEach { json.value(it) }
                    }
                    json.endArray()
                }
                json.endObject()
            }

            val rootView = app.getRootPresenter()?.view()
            if (rootView is GenericViewImpl) {
                json.name("contentViewId").value(rootView.instanceId)
            }
        }
        json.endObject()
    }
}
