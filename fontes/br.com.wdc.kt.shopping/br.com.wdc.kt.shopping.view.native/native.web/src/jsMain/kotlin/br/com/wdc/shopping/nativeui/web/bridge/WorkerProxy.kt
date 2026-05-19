package br.com.wdc.shopping.nativeui.web.bridge

import br.com.wdc.framework.commons.log.Log
import org.w3c.dom.Worker

/**
 * Main-thread bridge that communicates with the presenter Worker.
 * Receives state updates as JSON strings and dispatches actions.
 */
class WorkerProxy {

    companion object {
        private val LOG = Log.getLogger("WorkerProxy")
    }

    private var worker: Worker? = null

    /** Registry of viewId → view instance that can receive state updates */
    private val viewMap = mutableMapOf<String, ReactCubeView>()

    /**
     * Global retention map — keeps views alive until the Worker explicitly releases them.
     * Prevents garbage collection of views that are temporarily not rendered.
     */
    private val retainedViews = mutableMapOf<String, ReactCubeView>()

    /** Called when a new view is created in the Worker */
    var onViewCreated: ((viewId: String, viewType: String) -> Unit)? = null

    /** Called when a view is released in the Worker */
    var onViewReleased: ((viewId: String) -> Unit)? = null

    /** Called when the Worker updates browser history */
    var onHistoryUpdate: ((hash: String) -> Unit)? = null

    /** Called when the Worker is ready */
    var onReady: (() -> Unit)? = null

    fun start(workerUrl: String, baseUrl: String, initialHash: String) {
        val w = Worker(workerUrl)
        worker = w

        w.onmessage = { event ->
            val data = event.data.asDynamic()
            when (data.type as? String) {
                "stateUpdate" -> {
                    val viewId = data.viewId as String
                    val json = data.json as String
                    val view = viewMap[viewId]
                    if (view != null) {
                        view.readState(json)
                        view.update()
                    } else {
                        LOG.warn("stateUpdate for unknown viewId=$viewId")
                    }
                }

                "viewCreated" -> {
                    val viewId = data.viewId as String
                    val viewType = data.viewType as String
                    onViewCreated?.invoke(viewId, viewType)
                }

                "viewReleased" -> {
                    val viewId = data.viewId as String
                    val view = retainedViews.remove(viewId)
                    viewMap.remove(viewId)
                    view?.release()
                    onViewReleased?.invoke(viewId)
                }

                "historyUpdate" -> {
                    val hash = data.hash as String
                    onHistoryUpdate?.invoke(hash)
                }

                "ready" -> {
                    onReady?.invoke()
                }
            }
            Unit
        }

        // Send init message to Worker
        val msg = js("{}")
        msg.type = "init"
        msg.baseUrl = baseUrl
        msg.hash = initialHash
        w.postMessage(msg)
    }

    /**
     * Register a view instance to receive state updates for a given viewId.
     */
    fun registerView(viewId: String, view: ReactCubeView) {
        viewMap[viewId] = view
        retainedViews[viewId] = view
    }

    /**
     * Unregister a view.
     */
    fun unregisterView(viewId: String) {
        viewMap.remove(viewId)
    }

    /**
     * Look up a registered view by its ID.
     */
    fun getView(viewId: String): ReactCubeView? = viewMap[viewId]

    /**
     * Send an action to a presenter in the Worker.
     */
    fun action(viewId: String, method: String, vararg args: Any?) {
        val msg = js("{}")
        msg.type = "action"
        msg.viewId = viewId
        msg.method = method
        // Convert Kotlin types (Long, Int) to plain JS numbers for postMessage serialization.
        // Kotlin/JS IR represents Long as a wrapper object that doesn't survive structured clone.
        val jsArgs = js("[]")
        for (arg in args) {
            when (arg) {
                is Long -> jsArgs.push(arg.toDouble())
                is Int -> jsArgs.push(arg.toInt())
                else -> jsArgs.push(arg)
            }
        }
        msg.args = jsArgs
        worker?.postMessage(msg)
    }

    /**
     * Navigate to a path in the Worker's application.
     */
    fun navigate(path: String) {
        val msg = js("{}")
        msg.type = "navigate"
        msg.path = path
        worker?.postMessage(msg)
    }

    /**
     * Notify the Worker of a browser hash change.
     */
    fun hashChange(hash: String) {
        val msg = js("{}")
        msg.type = "hashChange"
        msg.hash = hash
        worker?.postMessage(msg)
    }

    fun terminate() {
        worker?.terminate()
        worker = null
        viewMap.clear()
    }
}
