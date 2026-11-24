package br.com.wdc.framework.cube

import br.com.wdc.framework.commons.concurrent.concurrentMutableMapOf
import kotlin.reflect.KClass

/**
 * The views a view layer keeps as objects (UIKit, Android views, the remote views of a session, test doubles): which
 * view draws each kind of [Scope], and the view of each scope on screen. The layer creates one, fills it and asks it
 * for the view of a scope ([viewOf]); the view is created the first time, repaints when its scope changes
 * ([CubeView.update]) and is released with its scope ([Scope.release]).
 *
 * ```kotlin
 * val views = ScopeViews()
 *     .register<CartScope> { CartViewIos(app, it) }
 *     .register<HomeScope> { HomeViewIos(app, views, it) }
 * ```
 *
 * The match is by the exact class of the scope; [fallback] answers the ones not registered. A layer whose views are
 * functions of the scope (Compose, React) does not need it: its components subscribe to the scope themselves.
 */
class ScopeViews {

    private val factories = mutableMapOf<KClass<out Scope>, (Scope) -> CubeView>()

    private var fallback: ((Scope) -> CubeView?)? = null

    private val views = concurrentMutableMapOf<Scope, CubeView>()

    fun <S : Scope> register(type: KClass<S>, factory: (S) -> CubeView): ScopeViews {
        @Suppress("UNCHECKED_CAST")
        factories[type] = { factory(it as S) }
        return this
    }

    inline fun <reified S : Scope> register(noinline factory: (S) -> CubeView): ScopeViews = register(S::class, factory)

    /** The view of a scope no entry names, or `null` when there is none. */
    fun fallback(factory: (Scope) -> CubeView?): ScopeViews {
        fallback = factory
        return this
    }

    /**
     * The view of [scope], created the first time it is asked for: it repaints on each change of the scope and is
     * released with it. A released scope gets a view that is not kept.
     */
    fun viewOf(scope: Scope): CubeView {
        views[scope]?.let { return it }
        val view = create(scope)
        if (scope.released) return view
        views[scope] = view
        val unsubscribe = scope.subscribe(view::update)
        scope.onRelease {
            unsubscribe()
            views.remove(scope)
            view.release()
        }
        return view
    }

    /** The view of [scope], if one was created. */
    fun viewOrNull(scope: Scope): CubeView? = views[scope]

    /** Every view kept. */
    val size: Int get() = views.size

    private fun create(scope: Scope): CubeView =
        factories[scope::class]?.invoke(scope)
            ?: fallback?.invoke(scope)
            ?: throw IllegalStateException("No view registered for ${scope::class.simpleName}")
}
