package br.com.wdc.store.nativeui.web.bridge

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.Scope
import kotlin.reflect.KClass
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import react.FC
import react.Key
import react.Props
import react.createRequiredContext
import react.memo
import react.useEffect
import react.useMemo
import react.useRequired
import react.useState

/** The props of a view: the scope it draws. */
external interface ScopeProps<S : Scope> : Props {
    var scope: S
}

/**
 * Which component draws each kind of [Scope]: the native web catalog of views. A view is a function component of its
 * scope, with no object behind it; it reads the scope through [useScope] and calls its actions through [safeCall].
 *
 * ```kotlin
 * val storeViews = ScopeComponents()
 *     .register(CartScope::class, CartView)
 * ```
 *
 * The match is by the exact class of the scope. A slot ([RenderSlot]) finds it through [ScopeComponentsContext].
 */
class ScopeComponents {

    private val views = mutableMapOf<KClass<out Scope>, FC<ScopeProps<Scope>>>()

    fun <S : Scope> register(type: KClass<S>, view: FC<ScopeProps<S>>): ScopeComponents {
        views[type] = view.unsafeCast<FC<ScopeProps<Scope>>>()
        return this
    }

    /** The component registered for the class of [scope]. */
    fun componentOf(scope: Scope): FC<ScopeProps<Scope>> =
        views[scope::class] ?: error("No view registered for ${scope::class.simpleName}")
}

/** The catalog the slots draw with: the entry point provides it around the root. */
val ScopeComponentsContext = createRequiredContext<ScopeComponents>()

/**
 * Draws the view of [RenderSlotProps.scope] (nothing for `null`), keyed by the scope: a different scope in the same
 * slot is a new component, with fresh UI state.
 *
 * **Memoized: a parent repaint does not repaint the slot.** Cube views are independent of each other; a view's
 * content only changes when its own scope does, and then the view repaints itself ([useScope]). The slot repaints
 * only when the parent puts a different scope in it. In a list, give each slot `key = scopeKey(row)`.
 */
val RenderSlot = memo(FC<RenderSlotProps> { props ->
    val scope = props.scope ?: return@FC
    val view = useRequired(ScopeComponentsContext).componentOf(scope)
    view {
        key = Key(scopeKey(scope))
        this.scope = scope
    }
})

external interface RenderSlotProps : Props {
    var scope: Scope?
}

/** A key that names [scope] among the React elements (the identity of the scope, as text). */
fun scopeKey(scope: Scope): String {
    val known = SCOPE_KEYS.get(scope)
    if (known != undefined) return known.unsafeCast<String>()
    val key = "s${nextScopeKey++}"
    SCOPE_KEYS.set(scope, key)
    return key
}

private val SCOPE_KEYS: dynamic = js("new WeakMap()")

private var nextScopeKey = 0

/**
 * Ties the calling component to [scope]: it repaints when the scope changes, once per paint cycle
 * ([ViewUpdateScheduler]). The component installs itself as the scope's updater as it starts rendering, before it
 * reads the scope — so no change is lost — and takes the updater back when it unmounts: there is no view to release,
 * React drops what leaves the screen.
 */
fun <S : Scope> useScope(scope: S): S {
    val setPainted = useState(0).component2()
    val painter = useMemo(scope) { Painter { setPainted { it + 1 } } }
    scope.setUpdater(painter.updater)
    // the effect's coroutine lives while the component is mounted: its cancellation is the cleanup
    useEffect(scope) {
        try {
            awaitCancellation()
        } finally {
            scope.clearUpdater(painter.updater)
            ViewUpdateScheduler.removeDirty(painter.repaint)
        }
    }
    return scope
}

/** The repaint of one component, and the updater a scope calls to ask for it. */
private class Painter(val repaint: () -> Unit) {
    val updater: () -> Unit = { ViewUpdateScheduler.markDirty(repaint) }
}

/**
 * Runs a call to the scope's actions on the presenters' serial dispatcher; an unexpected failure becomes the
 * application's alert. The paint cycle runs when it ends.
 */
fun safeCall(action: suspend () -> Unit) {
    val app = ViewUpdateScheduler.app
    app.presenterScope.launch {
        try {
            action()
        } catch (e: Exception) {
            app.alertUnexpectedError(LOG, "Unexpected error", e)
        } finally {
            ViewUpdateScheduler.flush()
        }
    }
}

private val LOG = Log.getLogger("NativeWebView")
