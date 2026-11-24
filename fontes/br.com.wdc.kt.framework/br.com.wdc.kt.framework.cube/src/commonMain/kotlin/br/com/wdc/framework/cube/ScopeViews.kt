package br.com.wdc.framework.cube

import kotlin.reflect.KClass

/**
 * The view layer's catalog: which view draws each kind of [Scope]. Each entry point fills one and hands it to the
 * application ([CubeApplication.scopeViews]); the application creates the view of a scope the first time it is asked
 * ([CubeApplication.viewOf]). Presenters know nothing about views.
 *
 * ```kotlin
 * app.scopeViews = ScopeViews()
 *     .register<CartScope> { CartView(app, it) }
 *     .register<HomeScope> { HomeView(app, it) }
 * ```
 *
 * The match is by the exact class of the scope; [fallback] answers the ones not registered.
 */
class ScopeViews {

    private val factories = mutableMapOf<KClass<out Scope>, (Scope) -> CubeView>()

    private var fallback: ((Scope) -> CubeView?)? = null

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

    /** A new view for [scope]. */
    fun create(scope: Scope): CubeView =
        factories[scope::class]?.invoke(scope)
            ?: fallback?.invoke(scope)
            ?: throw IllegalStateException("No view registered for ${scope::class.simpleName}")
}
