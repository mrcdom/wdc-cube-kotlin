package br.com.wdc.framework.cube

import br.com.wdc.framework.commons.log.Log
import kotlinx.coroutines.CancellationException

/**
 * What every presenter has: the application, the bound scope and the guarded actions. The presenter only makes
 * scopes: drawing them, and letting go of what drew them once they leave the screen, is up to the rendering
 * technology.
 *
 * An action is bound once, in the constructor, into a field of the scope (`scope.onBuy = action { buy() }`). The
 * guard catches whatever the body did not handle and hands it to [onActionFailure] (by default
 * [CubeApplication.alertUnexpectedError]); at the end, failing or not, the application republishes the address
 * ([CubeApplication.updateHistory]), since the action may have changed what the presenters publish. Expected failures
 * (an invalid item, an offline database) stay in the body, which turns them into messages of its own.
 *
 * An action a remote view fires is bound with its **event code**, and with how to read its values from the form data
 * ([FormParam]): `actionOf(2, FormParam.long("p.productId")) { removeProduct(it) }`. The code goes to the bound scope,
 * or to [Scope] `target` (a scope inside it), which dispatches it ([Scope.dispatch]).
 */
abstract class AbstractPresenter<A : CubeApplication>(override val app: A) : PresenterBase {

    private var boundScope: Scope? = null

    private val log by lazy { Log.getLogger(this::class.simpleName ?: "Presenter") }

    /** Nothing to let go of by default; a presenter that listens to something stops listening here. */
    override fun release() {}

    /** The bound scope ([bind]). */
    fun scope(): Scope? = boundScope

    /**
     * Ties [scope] to this presenter: the actions bound with an event code go to it. Used on the field:
     * `val scope = bind(CartScope())`.
     *
     * A smaller scope inside it (a panel, the rows of a list: a *segment*) needs no binding: the presenter creates
     * it, binds its actions (`actionOf(1, FormParam.long("p.productId"), target = products) { … }`) and puts it in a
     * field of the bound scope.
     */
    protected fun <S : Scope> bind(scope: S): S {
        boundScope = scope
        return scope
    }

    // :: Actions

    protected fun action(fn: suspend () -> Unit): Action = Action { guard { fn() } }

    protected fun <T> actionOf(fn: suspend (T) -> Unit): ActionOf<T> = ActionOf { guard { fn(it) } }

    protected fun <T, U> actionOf2(fn: suspend (T, U) -> Unit): ActionOf2<T, U> =
        ActionOf2 { first, second -> guard { fn(first, second) } }

    // :: Actions a remote view fires, by event code

    protected fun action(eventCode: Int, target: Scope = bound(), fn: suspend () -> Unit): Action =
        action(fn).also { action -> target.onEvent(eventCode) { action() } }

    protected fun <T> actionOf(eventCode: Int, param: FormParam<T>, target: Scope = bound(), fn: suspend (T) -> Unit): ActionOf<T> =
        actionOf(fn).also { action -> target.onEvent(eventCode) { action(param.read(it)) } }

    protected fun <T, U> actionOf2(
        eventCode: Int,
        first: FormParam<T>,
        second: FormParam<U>,
        target: Scope = bound(),
        fn: suspend (T, U) -> Unit,
    ): ActionOf2<T, U> =
        actionOf2(fn).also { action -> target.onEvent(eventCode) { action(first.read(it), second.read(it)) } }

    /**
     * Handles an event code of a remote view by hand, for an action whose values the form data can't give (they
     * arrive by [Scope.sync] first): `onEvent(1) { scope.onEnter(null, null) }`.
     */
    protected fun onEvent(eventCode: Int, target: Scope = bound(), handler: suspend (Map<String, Any?>) -> Unit) {
        target.onEvent(eventCode, handler)
    }

    /** A failure the action did not handle: the user sees "Unexpected error: <cause>", the log gets the rest. */
    protected open fun onActionFailure(caught: Exception) {
        app.alertUnexpectedError(log, "Unexpected error", caught)
    }

    private suspend fun guard(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onActionFailure(e)
        } finally {
            app.updateHistory()
        }
    }

    private fun bound(): Scope =
        boundScope ?: throw IllegalStateException("${this::class.simpleName}: bind the scope before its actions")
}
