package com.example.tasks.list

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.AbstractCubePresenter
import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.FormParam
import br.com.wdc.framework.cube.Scope
import br.com.wdc.framework.cube.ScopeSlot
import com.example.tasks.TasksApp
import com.example.tasks.data.Task
import com.example.tasks.data.TaskNotFoundException
import com.example.tasks.detail.TaskKeys
import com.example.tasks.list.calls.TaskCreate
import com.example.tasks.list.calls.TaskDoneUpdate
import com.example.tasks.list.calls.TaskListQuery

/**
 * The list of tasks. A presenter only makes scopes and binds their actions: it never creates, holds or releases a
 * view — drawing the scopes is up to each rendering technology.
 */
class TaskListPresenter(app: TasksApp) : AbstractCubePresenter<TasksApp>(app) {

    companion object {
        private val LOG = Log.getLogger("TaskListPresenter")
    }

    // :: Public Instance Fields

    val scope = bind(TaskListScope())

    // :: Internal Instance Fields

    private var ownerSlot: ScopeSlot? = null

    private val detailSlot = ScopeSlot { setDetail(it) }

    /** The rows alive, by task id: a row that stays is reused, so only what changed in it repaints. */
    private val rows = linkedMapOf<Long, TaskRowScope>()

    init {
        // the constructor only binds actions: each calls a method of the same name. The event code (and the form
        // value it reads) is what a remote view sends; a local view calls the action directly
        scope.onAdd = actionOf(1, FormParam.string("p.title")) { onAdd(it) }
    }

    // :: Cube API

    /**
     * Every navigation that goes through this place calls it: [initialization] the first time, [deepest] when this is
     * the screen asked for (no detail open).
     */
    override suspend fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean {
        val keys = TaskListKeys(app, intent)

        // read on every call: a reused presenter goes where its parent puts it now, not where it was first put
        ownerSlot = keys.parentSlot
        if (initialization || keys.taskChanged) {
            loadTasks()
        }

        ownerSlot?.setScope(scope)

        if (deepest) {
            setDetail(null)
        } else {
            keys.parentSlot = detailSlot
        }
        return true
    }

    /** Derived state, once per paint cycle, right before the scopes are read to be painted. */
    override fun commitComputedState() {
        scope.remaining = scope.rows.count { !it.done }
    }

    // :: User Actions

    private suspend fun onAdd(title: String?) {
        val text = title?.trim().orEmpty()
        if (text.isEmpty()) {
            errorTitleRequired()
            LOG.warn("onAdd.errorTitleRequired: {}", scope.errorMessage)
            return
        }

        clearError()
        val task = TaskCreate(title = text).run()
        scope.rows = scope.rows + newRow(task.id).fill(task)
    }

    private suspend fun onToggle(taskId: Long) {
        val row = rows[taskId] ?: return
        try {
            val task = TaskDoneUpdate(
                taskId = taskId,
                done = !row.done,
            ).run()
            row.fill(task)
            clearError()
        } catch (caught: TaskNotFoundException) {
            alertTaskNotFound()
            LOG.warn("onToggle.alertTaskNotFound: taskId={}", taskId)
            loadTasks()
        }
    }

    private suspend fun onOpen(taskId: Long) {
        TaskKeys(app).apply { this.taskId = taskId }.flip()
    }

    // :: Messages

    private fun errorTitleRequired() {
        scope.errorCode = 1
        scope.errorMessage = "Type what there is to do."
    }

    private fun alertTaskNotFound() {
        scope.errorCode = 2
        scope.errorMessage = "That task no longer exists."
    }

    private fun clearError() {
        scope.errorCode = 0
        scope.errorMessage = null
    }

    // :: Slots

    private fun setDetail(detail: Scope?) {
        if (scope.detail !== detail) {
            scope.detail = detail
        }
    }

    // :: Rows

    /**
     * Makes the rows follow the tasks: reuses the ones whose task stays (by id), creates the new ones, and forgets
     * the ones whose task left — taken out of the list, they leave the screen.
     */
    private suspend fun loadTasks() {
        val tasks = TaskListQuery().run()
        scope.rows = tasks.map { task -> rows.getOrPut(task.id) { newRow(task.id) }.fill(task) }
        rows.keys.retainAll(tasks.mapTo(HashSet()) { it.id })
    }

    private fun newRow(taskId: Long): TaskRowScope = TaskRowScope().also { row ->
        row.id = taskId
        row.onToggle = action(1, target = row) { onToggle(taskId) }
        row.onOpen = action(2, target = row) { onOpen(taskId) }
        rows[taskId] = row
    }

    private fun TaskRowScope.fill(task: Task): TaskRowScope = apply {
        title = task.title
        done = task.done
    }
}
