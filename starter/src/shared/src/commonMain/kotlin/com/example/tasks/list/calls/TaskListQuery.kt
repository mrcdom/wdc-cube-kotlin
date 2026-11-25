package com.example.tasks.list.calls

import br.com.wdc.framework.domain.resource.ResourceQuery
import com.example.tasks.data.Calls
import com.example.tasks.data.Task
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** Every task, in the order they were created. `POST /api/calls/task-list` → `[Task]`. */
open class TaskListQuery : ResourceQuery<List<Task>>() {

    companion object {
        const val PATH = "${Calls.BASE_PATH}/task-list"
    }

    @Serializable
    class Request

    override suspend fun run(): List<Task> = Calls.post(
        path = PATH,
        request = Request(),
        requestType = Request.serializer(),
        responseType = ListSerializer(Task.serializer()),
    )
}
