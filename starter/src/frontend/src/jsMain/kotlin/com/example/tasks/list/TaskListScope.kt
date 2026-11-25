package com.example.tasks.list

import br.com.wdc.framework.cube.Action
import br.com.wdc.framework.cube.ActionOf
import br.com.wdc.framework.cube.Scope

/**
 * The list screen: what its view draws and what it can ask for. A field (`by field`, `by scopes`) asks for a repaint
 * when it changes; actions are plain fields, bound by the presenter.
 */
class TaskListScope : Scope() {
    /** One row per task, each a scope of its own: a *segment* of this screen. */
    var rows: List<TaskRowScope> by scopes()

    /** Derived from the rows, in the presenter's `commitComputedState()`: computed once per paint. */
    var remaining: Int by field(0)

    /** The detail of the task open, if any: the slot this level offers the level below. */
    var detail: Scope? by field(null)

    var errorCode: Int by field(0)
    var errorMessage: String? by field(null)

    var onAdd: ActionOf<String?> = ActionOf.noop()
}

/** A row of the list. Scopes that make up the same screen live in the same file. */
class TaskRowScope : Scope() {
    var id: Long by field(0L)
    var title: String by field("")
    var done: Boolean by field(false)

    var onToggle: Action = Action.NOOP
    var onOpen: Action = Action.NOOP
}
