package com.example.tasks

import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubeKeys
import br.com.wdc.framework.cube.CubeTreePlace
import br.com.wdc.framework.cube.ScopeSlot

/**
 * The names a place reads from and writes to the intent, typed. **Each place has one keys class**, which declares the
 * place (in its companion, as a child of its parent's) and is the only one that knows its names; presenters navigate
 * through the keys of the destination (`TaskKeys(app).apply { taskId = 3 }.flip()`) and never write a name by hand.
 *
 * Every name starts with the class's two-letter prefix, unique in the application. Parameters (`P`) go in the address;
 * attributes (`A`) don't.
 */
open class RootKeys protected constructor(app: TasksApp, intent: CubeIntent?, place: CubeTreePlace) :
    CubeKeys<TasksApp>(app, intent, place) {

    constructor(app: TasksApp, intent: CubeIntent? = null) : this(app, intent, PLACE)

    companion object {
        const val PREFIX = "rt"

        /** The root: the application itself. */
        val PLACE: CubeTreePlace = CubeTreePlace.ROOT
    }

    private object A {
        const val PARENT_SLOT = "$PREFIX-parent-slot"
    }

    /**
     * Where this level shows its scope. Each level reads the slot it got, and republishes under the same name the slot
     * of its own content for the level below.
     */
    var parentSlot: ScopeSlot?
        get() = intent.getScopeSlot(A.PARENT_SLOT)
        set(value) = intent.setScopeSlot(A.PARENT_SLOT, value)
}
