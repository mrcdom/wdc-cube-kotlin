package com.example.tasks.backend

import br.com.wdc.framework.domain.exception.InvalidRequestException
import br.com.wdc.framework.rest.RawJson
import br.com.wdc.framework.rest.RestApi
import br.com.wdc.framework.rest.RestCall
import com.example.tasks.data.Calls
import com.example.tasks.data.Task
import com.example.tasks.detail.calls.TaskDelete
import com.example.tasks.detail.calls.TaskDeleteAction
import com.example.tasks.detail.calls.TaskQuery
import com.example.tasks.detail.calls.TaskQueryAction
import com.example.tasks.detail.calls.TaskTitleUpdate
import com.example.tasks.detail.calls.TaskTitleUpdateAction
import com.example.tasks.list.calls.TaskCreate
import com.example.tasks.list.calls.TaskCreateAction
import com.example.tasks.list.calls.TaskDoneUpdate
import com.example.tasks.list.calls.TaskDoneUpdateAction
import com.example.tasks.list.calls.TaskListQuery
import com.example.tasks.list.calls.TaskListQueryAction
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

/**
 * One route per call, at the call's path: it reads the request the frontend's call posted, runs the call's `…Action`
 * — the same call, answered with the repository — and writes the answer. Nothing else: what a call does lives in its
 * action. The framework maps what the action throws (`TaskNotFoundException` is a `NotFoundException`: 404).
 */
fun RestApi.Builder.callRoutes() {
    post(TaskListQuery.PATH) { _ ->
        reply(ListSerializer(Task.serializer()), TaskListQueryAction().run())
    }
    post(TaskCreate.PATH) { call ->
        val request = call.read(TaskCreate.Request.serializer())
        if (request.title.isBlank()) throw InvalidRequestException("A task needs a title")
        reply(Task.serializer(), TaskCreateAction(title = request.title).run())
    }
    post(TaskDoneUpdate.PATH) { call ->
        val request = call.read(TaskDoneUpdate.Request.serializer())
        val task = TaskDoneUpdateAction(
            taskId = request.taskId,
            done = request.done,
        ).run()
        reply(Task.serializer(), task)
    }
    post(TaskQuery.PATH) { call ->
        val request = call.read(TaskQuery.Request.serializer())
        reply(Task.serializer().nullable, TaskQueryAction(taskId = request.taskId).run())
    }
    post(TaskTitleUpdate.PATH) { call ->
        val request = call.read(TaskTitleUpdate.Request.serializer())
        if (request.title.isBlank()) throw InvalidRequestException("A task needs a title")
        val task = TaskTitleUpdateAction(
            taskId = request.taskId,
            title = request.title,
        ).run()
        reply(Task.serializer(), task)
    }
    post(TaskDelete.PATH) { call ->
        val request = call.read(TaskDelete.Request.serializer())
        reply(Boolean.serializer(), TaskDeleteAction(taskId = request.taskId).run())
    }
}

/** The request, as the frontend wrote it; a body that is not one is the caller's mistake (400). */
private fun <Q> RestCall.read(type: KSerializer<Q>): Q = try {
    Calls.json.decodeFromString(type, bodyText())
} catch (e: SerializationException) {
    throw InvalidRequestException("Not a valid request: ${e.message}")
} catch (e: IllegalArgumentException) {
    throw InvalidRequestException("Not a valid request: ${e.message}")
}

/** The answer, in the calls' JSON. */
private fun <R> reply(type: KSerializer<R>, value: R): RawJson = RawJson(Calls.json.encodeToString(type, value))
