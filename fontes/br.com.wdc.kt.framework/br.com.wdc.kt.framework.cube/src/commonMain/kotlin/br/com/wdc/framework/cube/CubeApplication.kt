package br.com.wdc.framework.cube

import br.com.wdc.framework.commons.concurrent.concurrentMutableMapOf
import br.com.wdc.framework.commons.log.Log

abstract class CubeApplication {

    internal var presenterMap: MutableMap<Int, CubePresenter> = concurrentMutableMapOf()

    internal var lastPlace: CubePlace? = null

    var fragment: String? = null
        protected set

    internal var navigation: CubeNavigation<*>? = null

    fun getPresenter(placeId: Int): CubePresenter? = presenterMap[placeId]

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

    @Suppress("UNCHECKED_CAST")
    fun <T : CubeApplication> navigate(): CubeNavigation<T> {
        val current = navigation
        if (current != null) {
            current.interrupt()

            if (current.reflowCount > 10) {
                throw AssertionError("Navigation recursion detected")
            }

            val newContext = CubeNavigation<T>(this)
            newContext.reflowCount = current.reflowCount + 1

            // Migrate created presenters from interrupted navigation
            newContext.newPresenterMap.putAll(current.newPresenterMap)

            navigation = newContext
            return newContext
        } else {
            val newContext = CubeNavigation<T>(this)
            navigation = newContext
            return newContext
        }
    }

    // Attributes — default in-memory storage (override for thread-safe or persistent implementations)

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
