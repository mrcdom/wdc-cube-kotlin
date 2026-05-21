package br.com.wdc.framework.cube

import br.com.wdc.framework.commons.log.Log

class CubeNavigation<T : CubeApplication> internal constructor(app: CubeApplication) {

    @Suppress("UNCHECKED_CAST")
    internal val app: T = app as T

    internal val curPresenterMap: MutableMap<Int, CubePresenter> = app.presenterMap
    internal val newPresenterMap: MutableMap<Int, CubePresenter> = HashMap()

    internal var reflowCount: Int = 1

    private var targetPlace: CubePlace? = null
    private var sourceIntent: CubeIntent = app.newIntent()
    private val steps: MutableList<CubePlace> = ArrayList()
    private var notInterrupted: Boolean = true

    fun step(place: CubePlace): CubeNavigation<T> {
        steps.add(place)
        return this
    }

    suspend fun execute(targetIntent: CubeIntent): Boolean {
        try {
            targetPlace = steps[steps.size - 1]

            var result = true
            val nextPresenters = ArrayList<PresenterHolder>()

            for (i in steps.indices) {
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

                val goAhead = holder.presenter!!.applyParameters(targetIntent, holder.initialize, holder.deepest)
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

    private suspend fun rollback(caught: Exception) {
        try {
            // Restore original presenters
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
            app.navigation = null
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
            app.lastPlace = targetPlace
            app.navigation = null
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
