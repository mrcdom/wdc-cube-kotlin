package br.com.wdc.store.view.react.skeleton.util

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.cube.Scope
import br.com.wdc.store.view.react.skeleton.viewimpl.ReactRootPresenter

/**
 * The remote view of a [Scope]: the client's events go to the actions the presenter bound with their codes
 * ([Scope.dispatch]), what the user typed to [Scope.sync], and the scope goes out as JSON with the ids of the views of
 * the scopes inside it. [vid] names the client component that paints it.
 */
open class ScopeViewImpl<S : Scope>(
    vid: String,
    app: ReactRootPresenter,
    val scope: S,
    instanceId: Int = app.nextInstanceId(),
) : GenericViewImpl(vid, app, instanceId) {

    override fun syncClientToServer(formData: Map<String, Any?>) {
        scope.sync(formData)
    }

    override suspend fun submit(eventCode: Int, eventQtde: Int, formData: Map<String, Any?>) {
        scope.dispatch(eventCode, formData)
    }

    override fun writeScope(json: ExtensibleObjectOutput) {
        scope.write(instanceId, json, app::viewOf)
    }
}
