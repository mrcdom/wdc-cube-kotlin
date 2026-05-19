package br.com.wdc.shopping.nativeui.web.worker

import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.framework.cube.ViewState

/**
 * CubeView implementation that runs inside a Web Worker.
 *
 * On [update], the view is marked dirty in the [WorkerUpdateScheduler].
 * The actual serialization and postMessage happens in a deferred microtask,
 * after [commitComputedState] is called on all presenters.
 */
class WorkerCubeView(
    private val viewId: String,
    val viewType: String,
    private val stateProvider: () -> ViewState
) : CubeView {

    override val instanceId: String get() = viewId

    override fun update() {
        WorkerUpdateScheduler.markDirty(this)
    }

    /**
     * Called by [WorkerUpdateScheduler] during flush.
     * Serializes the current state and posts it to the main thread.
     */
    fun flushState() {
        val state = stateProvider()
        val jsonOutput = JsonOutputFactory.createStringOutput()
        state.write(viewId, jsonOutput.output)
        val json = jsonOutput.resultString()

        val msg = js("{}")
        msg.type = "stateUpdate"
        msg.viewId = viewId
        msg.viewType = viewType
        msg.json = json
        js("self").postMessage(msg)
    }

    override fun release() {
        WorkerUpdateScheduler.removeDirty(this)
        val msg = js("{}")
        msg.type = "viewReleased"
        msg.viewId = viewId
        js("self").postMessage(msg)
    }
}
