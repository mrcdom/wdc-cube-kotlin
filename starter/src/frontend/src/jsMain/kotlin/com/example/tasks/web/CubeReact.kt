package com.example.tasks.web

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.Scope
import com.example.tasks.TasksApp
import kotlinx.browser.window
import kotlinx.coroutines.launch
import react.FC
import react.Props
import react.createRequiredContext
import react.memo
import react.useEffectWithCleanup
import react.useMemo
import react.useRequired
import react.useState
import kotlin.reflect.KClass

// The binding between the Cube and React: how scopes become components. Nothing here is specific to the tasks; it is
// the same in any application of this kind (the WDC Store's native web has a fuller one).

/**
 * The paint cycle. A scope that changes marks its component dirty; one animation frame paints them all, after the
 * application's `commitComputedState()` — once per cycle, right before painting.
 */
object PaintCycle {
    private val dirty = linkedSetOf<() -> Unit>()
    private var scheduled = false
    private var committing = false
    lateinit var app: TasksApp

    fun markDirty(repaint: () -> Unit) {
        dirty.add(repaint)
        if (committing || scheduled) return
        scheduled = true
        window.requestAnimationFrame { flush() }
    }

    fun forget(repaint: () -> Unit) {
        dirty.remove(repaint)
    }

    fun flush() {
        committing = true
        try {
            app.commitComputedState()
        } finally {
            committing = false
        }
        scheduled = false
        val snapshot = dirty.toList()
        dirty.clear()
        snapshot.forEach { it() }
    }
}

/** The props of a view: the scope it draws. */
external interface ScopeProps<S : Scope> : Props {
    var scope: S
}

/** Which component draws each kind of scope. A new screen is registered once, in [catalog]. */
class ScopeViews {
    private val views = mutableMapOf<KClass<out Scope>, FC<ScopeProps<Scope>>>()

    fun <S : Scope> register(type: KClass<S>, view: FC<ScopeProps<S>>): ScopeViews = apply {
        views[type] = view.unsafeCast<FC<ScopeProps<Scope>>>()
    }

    fun viewOf(scope: Scope): FC<ScopeProps<Scope>> =
        views[scope::class] ?: error("No view registered for ${scope::class.simpleName}")
}

val ScopeViewsContext = createRequiredContext<ScopeViews>()

external interface SlotProps : Props {
    var scope: Scope?
}

/**
 * Draws the view of a scope (nothing for `null`). Memoized: a parent's repaint does not repaint it; a view repaints
 * only when its own scope changes ([useScope]).
 */
val Slot = memo(FC<SlotProps> { props ->
    val scope = props.scope ?: return@FC
    val view = useRequired(ScopeViewsContext).viewOf(scope)
    view {
        key = keyOf(scope)
        this.scope = scope
    }
})

/** A React key for a scope: its identity, as text (give it to each slot of a list). */
fun keyOf(scope: Scope): String {
    val known = KEYS.get(scope)
    if (known != undefined) return known.unsafeCast<String>()
    val key = "s${nextKey++}"
    KEYS.set(scope, key)
    return key
}

private val KEYS: dynamic = js("new WeakMap()")
private var nextKey = 0

/**
 * Ties the component to its scope: it repaints when the scope changes. It installs itself as the scope's updater
 * before reading it (no change is lost) and takes it back when it unmounts — React drops what leaves the screen.
 */
fun <S : Scope> useScope(scope: S): S {
    val setPainted = useState(0).component2()
    val painter = useMemo(scope) { Painter { setPainted { it + 1 } } }
    scope.setUpdater(painter.updater)
    useEffectWithCleanup(scope) {
        onCleanup {
            scope.clearUpdater(painter.updater)
            PaintCycle.forget(painter.repaint)
        }
    }
    return scope
}

private class Painter(val repaint: () -> Unit) {
    val updater: () -> Unit = { PaintCycle.markDirty(repaint) }
}

/** Calls a scope's action from a UI event, in the presenters' coroutine; a failure becomes the application's alert. */
fun safeCall(action: suspend () -> Unit) {
    PaintCycle.app.presenterScope.launch {
        try {
            action()
        } catch (e: Exception) {
            PaintCycle.app.alertUnexpectedError(LOG, "Unexpected error", e)
        } finally {
            PaintCycle.flush()
        }
    }
}

private val LOG = Log.getLogger("CubeReact")
