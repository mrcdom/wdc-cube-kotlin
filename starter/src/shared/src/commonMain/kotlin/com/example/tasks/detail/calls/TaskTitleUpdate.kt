package com.example.tasks.detail.calls

import br.com.wdc.framework.domain.exception.NotFoundException
import br.com.wdc.framework.domain.resource.ResourceUpdate
import com.example.tasks.data.Calls
import com.example.tasks.data.Task
import com.example.tasks.data.TaskNotFoundException
import com.example.tasks.data.TaskTransactions
import kotlinx.serialization.Serializable

/**
 * Renames a task; [TaskNotFoundException] if it does not exist. Answers the task as it was saved.
 * `POST /api/calls/task-title-update` `{"taskId", "title"}` → `Task`; 404 if the task does not exist.
 */
open class TaskTitleUpdate(val taskId: Long, val title: String) :
    ResourceUpdate<Task>({ TaskTransactions.BEAN.getOrNull() }) {

    companion object {
        const val PATH = "${Calls.BASE_PATH}/task-title-update"
    }

    @Serializable
    class Request(val taskId: Long, val title: String)

    override suspend fun update(): Task = try {
        Calls.post(
            path = PATH,
            request = Request(
                taskId = taskId,
                title = title,
            ),
            requestType = Request.serializer(),
            responseType = Task.serializer(),
        )
    } catch (_: NotFoundException) {
        throw TaskNotFoundException(taskId)
    }
}
