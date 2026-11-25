package com.example.tasks.list.calls

import br.com.wdc.framework.domain.exception.NotFoundException
import br.com.wdc.framework.domain.resource.ResourceUpdate
import com.example.tasks.data.Calls
import com.example.tasks.data.Task
import com.example.tasks.data.TaskNotFoundException
import com.example.tasks.data.TaskTransactions
import kotlinx.serialization.Serializable

/**
 * Marks a task done or not; [TaskNotFoundException] if it does not exist. Answers the task as it was saved.
 * `POST /api/calls/task-done-update` `{"taskId", "done"}` → `Task`; 404 if the task does not exist.
 */
open class TaskDoneUpdate(val taskId: Long, val done: Boolean) :
    ResourceUpdate<Task>({ TaskTransactions.BEAN.getOrNull() }) {

    companion object {
        const val PATH = "${Calls.BASE_PATH}/task-done-update"
    }

    @Serializable
    class Request(val taskId: Long, val done: Boolean)

    override suspend fun update(): Task = try {
        Calls.post(
            path = PATH,
            request = Request(
                taskId = taskId,
                done = done,
            ),
            requestType = Request.serializer(),
            responseType = Task.serializer(),
        )
    } catch (_: NotFoundException) {
        throw TaskNotFoundException(taskId)
    }
}
