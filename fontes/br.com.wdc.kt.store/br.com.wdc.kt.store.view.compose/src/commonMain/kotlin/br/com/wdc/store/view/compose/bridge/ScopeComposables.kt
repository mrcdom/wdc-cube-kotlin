package br.com.wdc.store.view.compose.bridge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import br.com.wdc.framework.cube.Scope
import kotlin.reflect.KClass

/**
 * Which composable draws each kind of [Scope]: the Compose catalog of views. A view is a function of its scope, with
 * no object behind it; it reads the scope through [useScope] and calls its actions through [safeCall].
 *
 * ```kotlin
 * val storeViews = ScopeComposables()
 *     .register<CartScope> { CartView(it) }
 * ```
 *
 * The match is by the exact class of the scope. A slot ([RenderSlot]) finds it through [LocalScopeComposables].
 */
class ScopeComposables {

    private val views = mutableMapOf<KClass<out Scope>, @Composable (Scope) -> Unit>()

    fun <S : Scope> register(type: KClass<S>, view: @Composable (S) -> Unit): ScopeComposables {
        @Suppress("UNCHECKED_CAST")
        views[type] = { scope -> view(scope as S) }
        return this
    }

    inline fun <reified S : Scope> register(noinline view: @Composable (S) -> Unit): ScopeComposables =
        register(S::class, view)

    /** Draws [scope] with the composable registered for its class. */
    @Composable
    fun Render(scope: Scope) {
        val view = views[scope::class] ?: error("No view registered for ${scope::class.simpleName}")
        view(scope)
    }
}

/** The catalog the slots draw with: each entry point provides it around the root. */
val LocalScopeComposables = staticCompositionLocalOf<ScopeComposables> {
    error("No ScopeComposables provided: draw the root inside CompositionLocalProvider(LocalScopeComposables provides …)")
}

/**
 * Draws the view of [scope] (nothing for `null`), keyed by the scope: a different scope in the same slot is a new
 * view, with fresh UI state (`remember`). Views in slots are independent of their parent: the Compose compiler (strong
 * skipping) skips this call while the slot keeps the same scope, so a parent repaint does not repaint the child.
 *
 * In a list, key each row where the list calls it — `for (row in rows) key(row) { RenderSlot(row) }` — so a row keeps
 * its view when the rows before it come or go: the key inside this function only tells apart the scopes of one slot.
 */
@Composable
fun RenderSlot(scope: Scope?) {
    if (scope == null) return
    val views = LocalScopeComposables.current
    key(scope) { views.Render(scope) }
}

/**
 * Ties the calling composable to [scope]: it recomposes when the scope changes, once per paint cycle
 * ([ViewUpdateScheduler]). Subscribes when it enters the composition and unsubscribes when it leaves, so a view on
 * screen needs nothing else to start or stop: there is no view to release. A change between the first paint and the
 * subscription is not lost: the scope's [version][Scope.version] tells it.
 */
@Composable
fun <S : Scope> useScope(scope: S): S {
    val painted = remember(scope) { mutableIntStateOf(0) }
    painted.intValue // read here, so the scheduler's bump recomposes this view
    val seen = scope.version
    DisposableEffect(scope) {
        val unsubscribe = scope.subscribe { ViewUpdateScheduler.markDirty(painted) }
        if (scope.version != seen) ViewUpdateScheduler.markDirtyLater(painted)
        onDispose {
            unsubscribe()
            ViewUpdateScheduler.removeDirty(painted)
        }
    }
    return scope
}

/**
 * Runs a call to the scope's actions on the presenters' serial dispatcher, off the UI thread; an unexpected failure
 * becomes the application's alert. The paint cycle runs when it ends.
 */
fun safeCall(action: suspend () -> Unit) {
    ViewUpdateScheduler.safeCall(action)
}
