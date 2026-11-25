package com.example.tasks.detail

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.AbstractCubePresenter
import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.FormParam
import br.com.wdc.framework.cube.ScopeSlot
import com.example.tasks.TasksApp
import com.example.tasks.data.TaskNotFoundException
import com.example.tasks.detail.calls.TaskDelete
import com.example.tasks.detail.calls.TaskQuery
import com.example.tasks.detail.calls.TaskTitleUpdate
import com.example.tasks.list.TaskListKeys

/** The detail of one task, shown in the slot the list offers. */
class TaskPresenter(app: TasksApp) : AbstractCubePresenter<TasksApp>(app) {

    companion object {
        private val LOG = Log.getLogger("TaskPresenter")
    }

    // :: Public Instance Fields

    val scope = bind(TaskScope())

    // :: Internal Instance Fields

    private var taskId: Long? = null
    private var ownerSlot: ScopeSlot? = null

    init {
        scope.onRename = actionOf(1, FormParam.string("p.title")) { onRename(it) }
        scope.onRemove = action(2) { onRemove() }
        scope.onClose = action(3) { onClose() }
    }

    // :: Cube API

    override suspend fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean {
        val keys = TaskKeys(app, intent)
        val pTaskId = keys.taskId ?: taskId ?: run {
            // no usable id in the address: nothing to show; back to the list, with the intent in transit
            TaskListKeys(app, intent).flip()
            return false
        }

        // a navigation reuses this presenter for another task: load only when the task asked for changed
        if (initialization || pTaskId != taskId) {
            val task = TaskQuery(taskId = pTaskId).run() ?: run {
                LOG.warn("applyParameters: no task {}", pTaskId)
                TaskListKeys(app, intent).flip()
                return false
            }
            taskId = task.id
            scope.id = task.id
            scope.title = task.title
            scope.done = task.done
            clearError()
        }

        // read on every call: a reused presenter goes where its parent puts it now, not where it was first put
        ownerSlot = keys.parentSlot
        ownerSlot?.setScope(scope)
        return true
    }

    /** What this place puts in the address, so that a reload or a link opens the same task. */
    override fun publishParameters(intent: CubeIntent) {
        TaskKeys(app, intent).taskId = taskId
    }

    // :: User Actions

    private suspend fun onRename(title: String?) {
        val text = title?.trim().orEmpty()
        if (text.isEmpty()) {
            errorTitleRequired()
            LOG.warn("onRename.errorTitleRequired: {}", scope.errorMessage)
            return
        }

        try {
            TaskTitleUpdate(
                taskId = scope.id,
                title = text,
            ).run()
        } catch (caught: TaskNotFoundException) {
            LOG.warn("onRename: no task {}", scope.id)
        }
        // back to the list, telling it a task changed: it loads them again
        TaskListKeys(app).apply { taskChanged = true }.flip()
    }

    private suspend fun onRemove() {
        TaskDelete(taskId = scope.id).run()
        TaskListKeys(app).apply { taskChanged = true }.flip()
    }

    private suspend fun onClose() {
        TaskListKeys(app).flip()
    }

    // :: Messages

    private fun errorTitleRequired() {
        scope.errorCode = 1
        scope.errorMessage = "A task needs a title."
    }

    private fun clearError() {
        scope.errorCode = 0
        scope.errorMessage = null
    }
}
