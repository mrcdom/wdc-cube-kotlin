package com.example.tasks.detail.calls

import br.com.wdc.framework.domain.resource.ResourceQuery
import com.example.tasks.data.Calls
import com.example.tasks.data.Task
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable

/** One task; `null` if it does not exist. `POST /api/calls/task` `{"taskId"}` → `Task`, or `null`. */
open class TaskQuery(val taskId: Long) : ResourceQuery<Task?>() {

    companion object {
        const val PATH = "${Calls.BASE_PATH}/task"
    }

    @Serializable
    class Request(val taskId: Long)

    override suspend fun run(): Task? = Calls.post(
        path = PATH,
        request = Request(taskId = taskId),
        requestType = Request.serializer(),
        responseType = Task.serializer().nullable,
    )
}
