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

    /** Completa quando a navegação em curso (com os redirecionamentos dela) termina: `true` se comitou. */
    private var navigationDone: CompletableDeferred<Boolean>? = null

    /** O pedido de fora que espera a navegação em curso. Só o último espera; os anteriores desistem. */
    internal var waitingNavigation: CubeNavigation<*>? = null

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

    /**
     * Uma navegação nova; os passos vêm por [CubeNavigation.step] e ela corre em [CubeNavigation.execute].
     *
     * O que acontece com uma navegação em curso se decide no `execute`: pedida de dentro dela (o presenter, no
     * próprio `applyParameters`) é um redirecionamento e a interrompe; pedida de fora (ação da view, histórico)
     * espera ela terminar.
     */
    fun <T : CubeApplication> navigate(): CubeNavigation<T> = CubeNavigation(this)

    /** A navegação [nav] passa a ser a corrente, num período ocioso. */
    internal fun startNavigation(nav: CubeNavigation<*>) {
        navigation = nav
        navigationDone = CompletableDeferred()
    }

    /** Um redirecionamento toma o lugar da navegação corrente, no mesmo período. */
    internal fun replaceNavigation(nav: CubeNavigation<*>) {
        navigation = nav
    }

    /** A navegação corrente terminou (comitou, ou desfez com [success] `false`): acorda quem esperava. */
    internal fun endNavigation(success: Boolean) {
        navigation = null
        val done = navigationDone
        navigationDone = null
        done?.complete(success)
    }

    /** Espera a navegação corrente terminar; `true` se ela comitou. */
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
