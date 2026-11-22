package br.com.wdc.framework.cube

import br.com.wdc.framework.commons.log.Log
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

class CubeNavigation<T : CubeApplication> internal constructor(app: CubeApplication) {

    @Suppress("UNCHECKED_CAST")
    internal val app: T = app as T

    internal val curPresenterMap: MutableMap<Int, CubePresenter> = app.presenterMap
    internal val newPresenterMap: MutableMap<Int, CubePresenter> = HashMap()

    internal var reflowCount: Int = 1

    /** The place of the last accepted presenter: it is the current place after the commit. */
    private var acceptedPlace: CubePlace? = null
    private var sourceIntent: CubeIntent = app.newIntent()
    private val steps: MutableList<CubePlace> = ArrayList()
    private var notInterrupted: Boolean = true

    fun step(place: CubePlace): CubeNavigation<T> {
        steps.add(place)
        return this
    }

    suspend fun execute(targetIntent: CubeIntent): Boolean {
        if (!join()) {
            return false
        }
        try {
            var result = true
            val nextPresenters = ArrayList<PresenterHolder>()

            // the application first: it is the root of the tree, before every step
            val initialization = !app.applied
            val appGoesOn = applyIn { app.applyParameters(targetIntent, initialization, steps.isEmpty()) }
            app.applied = true
            if (!appGoesOn || !notInterrupted) {
                result = false
            }

            for (i in steps.indices) {
                if (!result) break

                val place = steps[i]
                val deepest = i == steps.size - 1

                targetIntent.place = place

                val holder = PresenterHolder()
                holder.id = place.id
                holder.deepest = deepest

                val presenter = curPresenterMap[place.id] ?: newPresenterMap[place.id]
                if (presenter == null) {
                    val factory = place.presenterFactory<T>()
                    val newPresenter = factory(app)
                    newPresenterMap[place.id] = newPresenter
                    holder.presenter = newPresenter
                    holder.initialize = true
                } else {
                    holder.presenter = presenter
                    holder.initialize = false
                }

                nextPresenters.add(holder)
                // false = the presenter was accepted, but the composition stops at it: it is the current place
                acceptedPlace = place

                val presenterInStep = holder.presenter!!
                val goAhead = applyIn { presenterInStep.applyParameters(targetIntent, holder.initialize, holder.deepest) }
                if (!goAhead || !notInterrupted) {
                    result = false
                    break
                }
            }

            if (notInterrupted) {
                commit(nextPresenters)
            }
            return result
        } catch (caught: Exception) {
            if (notInterrupted) {
                rollback(caught)
            }
            throw caught
        }
    }

    fun interrupt() {
        notInterrupted = false
    }

    /**
     * Takes the current navigation slot.
     *
     * - None running: starts.
     * - Requested from inside the current one (the coroutine context marks it: it is a presenter in
     *   `applyParameters`): it is a redirect — interrupts the current one and inherits the presenters it created.
     * - Requested from outside: waits for the current one to finish. Only the last request waits; an earlier one
     *   gives up (`false`), and the waiting one also gives up if the current one fails.
     *
     * @return `false` when it gave up and must not navigate
     */
    private suspend fun join(): Boolean {
        val current = app.navigation
        if (current == null) {
            app.startNavigation(this)
            return true
        }
        if (currentCoroutineContext()[Marker]?.navigation === current) {
            current.interrupt()
            if (current.reflowCount > 10) {
                throw AssertionError("Navigation recursion detected")
            }
            reflowCount = current.reflowCount + 1
            newPresenterMap.putAll(current.newPresenterMap)
            app.replaceNavigation(this)
            return true
        }
        app.waitingNavigation = this
        while (app.navigation != null) {
            val committed = app.awaitNavigation()
            if (app.waitingNavigation !== this) {
                return false // a newer request took the slot
            }
            if (!committed) {
                app.waitingNavigation = null
                return false // the navigation this request depended on failed
            }
        }
        app.waitingNavigation = null
        app.startNavigation(this)
        return true
    }

    /**
     * `applyParameters` with this navigation marked in the context: a `navigate` made in there is a redirect.
     * The failure is caught inside `withContext` and rethrown outside, so it arrives as the same instance (with
     * `-ea`, the exception that crosses `withContext` is replaced by a copy).
     */
    private suspend fun applyIn(apply: suspend () -> Boolean): Boolean {
        var failure: Throwable? = null
        val result = withContext(Marker(this)) {
            try {
                apply()
            } catch (e: Throwable) {
                failure = e
                false
            }
        }
        failure?.let { throw it }
        return result
    }

    /** Marks, in the coroutine context, the navigation whose `applyParameters` is running. */
    private class Marker(val navigation: CubeNavigation<*>) : AbstractCoroutineContextElement(Marker) {
        companion object Key : CoroutineContext.Key<Marker>
    }

    private suspend fun rollback(caught: Exception) {
        try {
            // Restore the application, then the original presenters
            try {
                app.applyParameters(sourceIntent, false, false)
            } catch (otherCaught: Exception) {
                LOG.debug("Restoring source state of the application: ${caught.message}")
                caught.addSuppressed(otherCaught)
            }
            val presenterIds = curPresenterMap.keys.sorted().toList()
            for (i in presenterIds.indices) {
                val presenterId = presenterIds[i]
                try {
                    val presenter = curPresenterMap[presenterId]
                    if (presenter != null) {
                        presenter.applyParameters(sourceIntent, false, i == presenterIds.size - 1)
                    } else {
                        LOG.debug("Missing presenter for ID=$presenterId")
                    }
                } catch (otherCaught: Exception) {
                    LOG.debug("Restoring source state: ${caught.message}")
                    caught.addSuppressed(otherCaught)
                }
            }

            // Release all created presenters (leaf-first)
            if (newPresenterMap.isNotEmpty()) {
                releasePresenters(newPresenterMap)
            }
        } finally {
            app.updateHistory()
            app.endNavigation(success = false)
        }
    }

    private fun commit(nextPresenters: List<PresenterHolder>) {
        try {
            // Build the final presenter map from accepted presenters
            val finalMap = HashMap<Int, CubePresenter>()
            for (holder in nextPresenters) {
                finalMap[holder.id] = holder.presenter!!
                newPresenterMap.remove(holder.id)
                curPresenterMap.remove(holder.id)
            }

            // Release non-accepted created and replaced presenters (leaf-first)
            curPresenterMap.putAll(newPresenterMap)
            if (curPresenterMap.isNotEmpty()) {
                releasePresenters(curPresenterMap)
            }

            // Update the existing map in-place (preserves thread-safe wrapper)
            app.presenterMap.clear()
            app.presenterMap.putAll(finalMap)
        } finally {
            app.lastPlace = acceptedPlace
            app.endNavigation(success = true)
            app.updateHistory()
        }
    }

    private class PresenterHolder {
        var id: Int = 0
        var presenter: CubePresenter? = null
        var initialize: Boolean = false
        var deepest: Boolean = false
    }

    companion object {
        private val LOG = Log.getLogger("CubeNavigation")

        private fun releasePresenters(presenterMap: MutableMap<Int, CubePresenter>) {
            val presenterIds = presenterMap.keys.sortedDescending().toList()

            for (presenterId in presenterIds) {
                val presenter = presenterMap.remove(presenterId)
                if (presenter != null) {
                    try {
                        presenter.release()
                    } catch (caught: Exception) {
                        LOG.error("Releasing presenter: ${caught.message}")
                    }
                }
            }
        }
    }
}
