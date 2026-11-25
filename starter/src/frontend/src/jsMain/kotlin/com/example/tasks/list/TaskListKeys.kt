package com.example.tasks.list

import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubeTreePlace
import com.example.tasks.RootKeys
import com.example.tasks.TasksApp

/** The list of tasks: the first level. A place with children takes their place in a protected constructor. */
open class TaskListKeys protected constructor(app: TasksApp, intent: CubeIntent?, place: CubeTreePlace) :
    RootKeys(app, intent, place) {

    constructor(app: TasksApp, intent: CubeIntent? = null) : this(app, intent, PLACE)

    companion object {
        const val PREFIX = "tl"

        val PLACE: CubeTreePlace = RootKeys.PLACE.child("tasks", ::TaskListPresenter)
    }

    private object A {
        /** Set by whoever changed a task elsewhere (the detail): the list loads them again. */
        const val TASK_CHANGED = "$PREFIX-task-changed"
    }

    /** An attribute, not a parameter: it travels with this navigation only, never in the address. */
    var taskChanged: Boolean
        get() = intent.getAttribute(A.TASK_CHANGED) == true
        set(value) = intent.setAttribute(A.TASK_CHANGED, value.takeIf { it })
}
