package com.example.tasks.list.calls

import br.com.wdc.framework.domain.resource.ResourceCreate
import com.example.tasks.data.Calls
import com.example.tasks.data.Task
import com.example.tasks.data.TaskTransactions
import kotlinx.serialization.Serializable

/** A new task, not done. `POST /api/calls/task-create` `{"title"}` → `Task`. */
open class TaskCreate(val title: String) : ResourceCreate<Task>({ TaskTransactions.BEAN.getOrNull() }) {

    companion object {
        const val PATH = "${Calls.BASE_PATH}/task-create"
    }

    @Serializable
    class Request(val title: String)

    override suspend fun create(): Task = Calls.post(
        path = PATH,
        request = Request(title = title),
        requestType = Request.serializer(),
        responseType = Task.serializer(),
    )
}
