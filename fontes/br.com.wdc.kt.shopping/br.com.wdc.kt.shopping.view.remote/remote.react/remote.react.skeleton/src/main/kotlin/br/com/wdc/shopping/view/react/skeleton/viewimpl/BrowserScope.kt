package br.com.wdc.shopping.view.react.skeleton.viewimpl

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.cube.Action
import br.com.wdc.framework.cube.ActionOf
import br.com.wdc.framework.cube.Scope
import br.com.wdc.framework.cube.ScopeValue

/**
 * The browser tab of the remote client: the page frame around the root view (the alert) and the client's own events
 * (start, history, keep-alive, the alert's OK). It belongs to the remote layer: the presentation knows nothing of it.
 */
class BrowserScope : Scope() {

    /** The alert on screen, or none. */
    var alertMessage: BrowserAlert? by field(null)

    /** The root presenter's scope: goes out as "contentViewId". */
    var content: Scope? by field(null)

    var onAlertOk: Action = Action.NOOP
    var onKeepAlive: Action = Action.NOOP
    var onStart: ActionOf<String?> = ActionOf.noop()
    var onHistoryChanged: ActionOf<String?> = ActionOf.noop()
}

/** An alert of the browser tab: the client turns [id] and [args] into its text (-1 = unexpected error). */
class BrowserAlert(val id: Int, val args: List<String>) : ScopeValue {
    override fun writeJson(json: ExtensibleObjectOutput) {
        json.beginObject()
        json.name("id").value(id.toLong())
        json.name("args").beginArray()
        args.forEach { json.value(it) }
        json.endArray()
        json.endObject()
    }
}
