package br.com.wdc.store.view.react.skeleton.util

import br.com.wdc.framework.cube.Scope

/**
 * What a remote view needs from its session: the views of the scopes it holds, and telling the client what changed or
 * left. The session (`ReactRootPresenter`) is the rendering technology of the remote client.
 */
interface RemoteApplication {

    /** A new instance number, for the id of a new view. */
    fun nextInstanceId(): Int

    /**
     * The view that draws [scope] for the client, made the first time a scope that holds it is written: from then on,
     * a change in the scope sends it again.
     */
    fun viewOf(scope: Scope): ScopeViewImpl<*>

    /** The view was released: it no longer draws its scope, and it leaves the views to send. */
    fun forgetView(view: ScopeViewImpl<*>)

    /** The client is told to drop the view ([instanceId] goes out in `releasedViews`). */
    fun removeView(instanceId: String)

    /** The view's scope changed: the client needs it again. */
    fun markDirty(view: ScopeViewImpl<*>)
}
