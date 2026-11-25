package com.example.tasks.detail

import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubeTreePlace
import com.example.tasks.TasksApp
import com.example.tasks.list.TaskListKeys

/** The detail of one task, below the list: `task?tk-id=3`. */
class TaskKeys(app: TasksApp, intent: CubeIntent? = null) : TaskListKeys(app, intent, PLACE) {

    companion object {
        const val PREFIX = "tk"

        val PLACE: CubeTreePlace = TaskListKeys.PLACE.child("task", ::TaskPresenter)
    }

    private object P {
        const val TASK_ID = "$PREFIX-id"
    }

    /**
     * A positive id, or `null`. The address is input from outside: the getter validates, and never throws.
     */
    var taskId: Long?
        get() = intent.getParameterAsLong(P.TASK_ID)?.takeIf { it > 0 }
        set(value) = intent.setParameter(P.TASK_ID, value?.takeIf { it > 0 })
}
