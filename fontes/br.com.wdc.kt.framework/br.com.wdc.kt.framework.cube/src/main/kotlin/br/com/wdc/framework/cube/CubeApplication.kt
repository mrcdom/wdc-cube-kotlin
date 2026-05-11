package br.com.wdc.framework.cube

import br.com.wdc.framework.commons.log.Log

abstract class CubeApplication {

    internal var presenterMap: MutableMap<Int, CubePresenter> = createPresenterMap()

    internal var lastPlace: CubePlace? = null

    var fragment: String? = null
        protected set

    internal var navigation: CubeNavigation<*>? = null

    fun getPresenter(placeId: Int): CubePresenter? = presenterMap[placeId]

    fun release() {
        val presenterIds = presenterMap.keys.sortedDescending().toList()

        for (presenterId in presenterIds) {
            val presenter = presenterMap.remove(presenterId)
            if (presenter != null) {
                try {
                    presenter.release()
                } catch (caught: Exception) {
                    LOG.error("releasing ${presenter.javaClass}: ${caught.message}")
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

    fun commitComputedState() {
        for (presenter in presenterMap.values) {
            try {
                presenter.commitComputedState()
            } catch (caught: Exception) {
                LOG.error("Processing ${presenter.javaClass.simpleName}: ${caught.message}")
            }
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
            navigation = newContext
            return newContext
        } else {
            val newContext = CubeNavigation<T>(this)
            navigation = newContext
            return newContext
        }
    }

    // Abstract

    abstract fun setAttribute(name: String, value: Any?): Any?

    abstract fun getAttribute(name: String): Any?

    abstract fun removeAttribute(name: String): Any?

    abstract fun updateHistory()

    protected abstract fun createPresenterMap(): MutableMap<Int, CubePresenter>

    private companion object {
        val LOG = Log.getLogger(CubeApplication::class.java.simpleName)
    }
}
