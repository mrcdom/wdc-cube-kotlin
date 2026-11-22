package br.com.wdc.framework.cube

import br.com.wdc.framework.commons.concurrent.concurrentMutableMapOf
import br.com.wdc.framework.commons.log.Log
import kotlinx.coroutines.CompletableDeferred

abstract class CubeApplication {

    internal var presenterMap: MutableMap<Int, CubePresenter> = concurrentMutableMapOf()

    internal var lastPlace: CubePlace? = null

    var fragment: String? = null
        protected set

    internal var navigation: CubeNavigation<*>? = null

    /** Completes when the running navigation (with its redirects) finishes: `true` if it committed. */
    private var navigationDone: CompletableDeferred<Boolean>? = null

    /** The outside request waiting for the running navigation. Only the last one waits; the earlier ones give up. */
    internal var waitingNavigation: CubeNavigation<*>? = null

    /** Whether the application has had its first [applyParameters]. */
    internal var applied: Boolean = false

    fun getPresenter(placeId: Int): CubePresenter? = presenterMap[placeId]

    /**
     * The application's own part in every navigation, run before the first step: the application is the root of the
     * place tree ([CubeTreePlace.ROOT]) — it may own the page frame (alerts, the slot of the first level) and hand it
     * down through the intent, as a presenter does. A navigation requested from in here is a redirect.
     *
     * @param initialization `true` only the first time
     * @param deepest `true` when the navigation goes to the root itself (no steps)
     * @return `false` to stop the navigation at the root
     */
    open suspend fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean = true

    open fun release() {
        val presenterIds = presenterMap.keys.sortedDescending().toList()

        for (presenterId in presenterIds) {
            val presenter = presenterMap.remove(presenterId)
            if (presenter != null) {
                try {
                    presenter.release()
                } catch (caught: Exception) {
                    LOG.error("releasing ${presenter::class.simpleName}: ${caught.message}")
                }
            }
        }

        presenterMap.clear()
    }

    fun publishParameters(intent: CubeIntent) {
        for (presenter in presenterMap.values) {
            presenter.publishParameters(intent)
        }
    }

    fun newIntent(): CubeIntent {
        val intent = CubeIntent()
        intent.place = lastPlace
        publishParameters(intent)
        return intent
    }

    /**
     * A new navigation; the steps come through [CubeNavigation.step] and it runs in [CubeNavigation.execute].
     *
     * What happens to a running navigation is decided in `execute`: requested from inside it (the presenter, in
     * its own `applyParameters`) it is a redirect and interrupts it; requested from outside (view action, history)
     * it waits for it to finish.
     */
    fun <T : CubeApplication> navigate(): CubeNavigation<T> = CubeNavigation(this)

    /**
     * Navigates to [place], stepping through its [path][CubeTreePlace.path] from the root, with [intent] (by default
     * a new one, from the current state). Applications usually call it through their keys ([CubeKeys.flip]). To
     * [CubeTreePlace.ROOT], only the application's [applyParameters] runs.
     *
     * @return `true` if the whole path was accepted
     */
    suspend fun flip(place: CubeTreePlace, intent: CubeIntent = newIntent()): Boolean {
        val navigation = navigate<CubeApplication>()
        for (step in place.path) navigation.step(step)
        return navigation.execute(intent)
    }

    /** Navigation [nav] becomes the current one, during an idle period. */
    internal fun startNavigation(nav: CubeNavigation<*>) {
        navigation = nav
        navigationDone = CompletableDeferred()
    }

    /** A redirect takes the place of the current navigation, in the same period. */
    internal fun replaceNavigation(nav: CubeNavigation<*>) {
        navigation = nav
    }

    /** The current navigation finished (committed, or rolled back with [success] `false`): wakes whoever was waiting. */
    internal fun endNavigation(success: Boolean) {
        navigation = null
        val done = navigationDone
        navigationDone = null
        done?.complete(success)
    }

    /** Waits for the current navigation to finish; `true` if it committed. */
    internal suspend fun awaitNavigation(): Boolean = navigationDone?.await() ?: true

    private val attributes: MutableMap<String, Any?> = concurrentMutableMapOf()

    open fun setAttribute(name: String, value: Any?): Any? = attributes.put(name, value)

    open fun getAttribute(name: String): Any? = attributes[name]

    open fun removeAttribute(name: String): Any? = attributes.remove(name)

    open fun updateHistory() {}

    fun forEachPresenter(action: (CubePresenter) -> Unit) {
        presenterMap.values.forEach(action)
    }

    protected fun getLastPlace(): CubePlace? = lastPlace

    private companion object {
        val LOG = Log.getLogger("CubeApplication")
    }
}
