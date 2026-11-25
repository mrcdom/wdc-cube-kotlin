package br.com.wdc.framework.cube

import br.com.wdc.framework.commons.concurrent.concurrentMutableMapOf
import br.com.wdc.framework.commons.log.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * The application: the presenters alive, the navigation in progress, and the root of the place tree.
 *
 * @param places every place an address can name ([flip] by address resolves names among them; an unknown name goes
 *   to [CubeTreePlace.ROOT]). Listed by hand: a place declared in a keys class is only created when that class is
 *   first used, and listing it creates it before the first navigation.
 */
abstract class CubeApplication(places: List<CubeTreePlace> = emptyList()) {

    /** The places an address can name. */
    val places: List<CubeTreePlace> = places.toList()

    private val placesByName: Map<String, CubeTreePlace> = this.places.associateBy { it.placeName }

    init {
        require(placesByName.size == this.places.size) { "Two places with the same name: ${this.places}" }
        require(this.places.none { it.isRoot }) { "The root is the application itself: do not list it" }
    }

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

    /** One action at a time ([runAction]). */
    private val actionMutex = Mutex()

    /**
     * Runs [block] as the application's only action in progress: an action that starts while another waits on I/O
     * waits for it to finish, so two clicks on "Buy" make one purchase after the other, never interleaved, and an
     * action never sees another one's state halfway. No thread is blocked meanwhile. An action that starts another
     * (directly, or in a coroutine of its own) runs it at once: the lock goes along with the coroutine context.
     * The guarded actions of [AbstractPresenter] run through here.
     */
    suspend fun <T> runAction(block: suspend () -> T): T {
        if (currentCoroutineContext()[ActionInProgress]?.app === this) return block()
        return actionMutex.withLock { withContext(ActionInProgress(this)) { block() } }
    }

    /** The mark of an action in progress in a coroutine's context. */
    private class ActionInProgress(val app: CubeApplication) : AbstractCoroutineContextElement(ActionInProgress) {
        companion object Key : CoroutineContext.Key<ActionInProgress>
    }

    /**
     * Runs every live presenter's `commitComputedState()`: the view layer calls it once per paint cycle, right before
     * it reads the scopes to paint.
     */
    open fun commitComputedState() {
        for (presenter in presenterMap.values) {
            try {
                presenter.commitComputedState()
            } catch (caught: Exception) {
                LOG.error("commitComputedState of ${presenter::class.simpleName}: ${caught.message}")
            }
        }
    }

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

    /** The place named [placeName] among [places], or [CubeTreePlace.ROOT] when none has it. */
    fun placeOf(placeName: String?): CubeTreePlace = placesByName[placeName] ?: CubeTreePlace.ROOT

    /** Navigates to an address (`product?pr-id=7`): the place it names, with its parameters. See [placeOf]. */
    suspend fun flip(address: String): Boolean = flip(CubeIntent.parse(address))

    /** Navigates to the place [intent] names, resolved by name ([placeOf]), with its parameters. */
    suspend fun flip(intent: CubeIntent): Boolean = flip(placeOf(intent.place?.placeName), intent)

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

    /**
     * A failure nobody handled (the guard of an action hands it here): the application shows it as it can. By
     * default it is only logged.
     */
    open fun alertUnexpectedError(logger: Log, message: String, e: Throwable) {
        logger.error(message, e)
    }

    fun forEachPresenter(action: (CubePresenter) -> Unit) {
        presenterMap.values.forEach(action)
    }

    protected fun getLastPlace(): CubePlace? = lastPlace

    private companion object {
        val LOG = Log.getLogger("CubeApplication")
    }
}
