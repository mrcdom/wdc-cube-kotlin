package br.com.wdc.framework.cube.remote

import br.com.wdc.framework.commons.log.Log

/**
 * An application whose presenters run on the server and whose views run in a browser: one per client session. The
 * application keeps extending its own root presenter (or `CubeApplication`) and holds a [RemoteApplicationSupport],
 * which does the session's work — the views, the protocol, the history; the registry and the host talk to it through
 * [remote].
 *
 * ```kotlin
 * class ShopRemoteApplication(id: String) : ShopRootPresenter(), RemoteApplication {
 *     override val remote = RemoteApplicationSupport(id, this, VIDS, content = scope)
 *
 *     override fun alertUnexpectedError(logger: Log, message: String, e: Throwable) {
 *         logger.error(message, e)
 *         remote.browser.alertUnexpectedError(message)
 *     }
 *
 *     override fun release() {
 *         remote.release()
 *         super.release()
 *     }
 * }
 * ```
 */
interface RemoteApplication {

    /** The session's work: views, protocol, history and life. */
    val remote: RemoteApplicationSupport

    /**
     * Whether the session outlives its connection. Without it, a closed WebSocket releases the application; with it,
     * the application waits for the client to reconnect, until it expires. Typically: a signed-in user's session.
     */
    fun keepsSessionWithoutConnection(): Boolean = false

    /** Shows an unexpected failure to the user — the text only, in the browser's alert ([RemoteBrowserPresenter]). */
    fun alertUnexpectedError(logger: Log, message: String, e: Throwable)

    /** Releases the application: its presenters, and the session ([RemoteApplicationSupport.release]). */
    fun release()
}
