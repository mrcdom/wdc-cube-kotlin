package com.example.tasks.detail.calls

import br.com.wdc.framework.domain.resource.ResourceDelete
import com.example.tasks.data.Calls
import com.example.tasks.data.TaskTransactions
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

/** Removes a task; answers whether it existed. `POST /api/calls/task-delete` `{"taskId"}` → `true`/`false`. */
open class TaskDelete(val taskId: Long) : ResourceDelete<Boolean>({ TaskTransactions.BEAN.getOrNull() }) {

    companion object {
        const val PATH = "${Calls.BASE_PATH}/task-delete"
    }

    @Serializable
    class Request(val taskId: Long)

    override suspend fun delete(): Boolean = Calls.post(
        path = PATH,
        request = Request(taskId = taskId),
        requestType = Request.serializer(),
        responseType = Boolean.serializer(),
    )
}
