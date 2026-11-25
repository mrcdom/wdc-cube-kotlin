package com.example.tasks.detail

import br.com.wdc.framework.cube.Action
import br.com.wdc.framework.cube.ActionOf
import br.com.wdc.framework.cube.Scope

/** The detail of a task. */
class TaskScope : Scope() {
    var id: Long by field(0L)
    var title: String by field("")
    var done: Boolean by field(false)

    var errorCode: Int by field(0)
    var errorMessage: String? by field(null)

    var onRename: ActionOf<String?> = ActionOf.noop()
    var onRemove: Action = Action.NOOP
    var onClose: Action = Action.NOOP
}
