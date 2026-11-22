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

    /** O lugar do último presenter aceito: é o lugar corrente depois do commit. */
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
                // false = o presenter foi aceito, mas a composição para nele: ele é o lugar corrente
                acceptedPlace = place

                val goAhead = applyIn(holder.presenter!!, targetIntent, holder.initialize, holder.deepest)
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
     * Ocupa o lugar de navegação corrente.
     *
     * - Nenhuma em curso: começa.
     * - Pedida de dentro da corrente (o contexto da corrotina a marca: é um presenter no `applyParameters`): é um
     *   redirecionamento — interrompe a corrente e herda os presenters que ela criou.
     * - Pedida de fora: espera a corrente terminar. Só o último pedido espera; um anterior desiste (`false`), e o
     *   que espera também desiste se a corrente falhar.
     *
     * @return `false` quando desistiu e não deve navegar
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
                return false // um pedido mais novo tomou o lugar
            }
            if (!committed) {
                app.waitingNavigation = null
                return false // a navegação de que este pedido dependia falhou
            }
        }
        app.waitingNavigation = null
        app.startNavigation(this)
        return true
    }

    /**
     * O `applyParameters` com esta navegação marcada no contexto: o `navigate` feito lá dentro é redirecionamento.
     * A falha é capturada dentro do `withContext` e relançada fora, para chegar como a mesma instância (com `-ea`,
     * a exceção que atravessa o `withContext` é trocada por uma cópia).
     */
    private suspend fun applyIn(presenter: CubePresenter, intent: CubeIntent, initialize: Boolean, deepest: Boolean): Boolean {
        var failure: Throwable? = null
        val result = withContext(Marker(this)) {
            try {
                presenter.applyParameters(intent, initialize, deepest)
            } catch (e: Throwable) {
                failure = e
                false
            }
        }
        failure?.let { throw it }
        return result
    }

    /** Marca, no contexto da corrotina, a navegação cujo `applyParameters` está rodando. */
    private class Marker(val navigation: CubeNavigation<*>) : AbstractCoroutineContextElement(Marker) {
        companion object Key : CoroutineContext.Key<Marker>
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
