package com.example.tasks

import br.com.wdc.framework.domain.exception.NotFoundException
import com.example.tasks.data.CallHttpClient
import com.example.tasks.data.Calls
import com.example.tasks.data.Task
import com.example.tasks.detail.calls.TaskDelete
import com.example.tasks.detail.calls.TaskQuery
import com.example.tasks.detail.calls.TaskTitleUpdate
import com.example.tasks.list.calls.TaskCreate
import com.example.tasks.list.calls.TaskDoneUpdate
import com.example.tasks.list.calls.TaskListQuery
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

/**
 * The backend, for the presenter tests: it answers the calls' paths in memory, reading and writing the same requests
 * and JSON the real one does. Registered as the [CallHttpClient], it lets the presenters run with no server.
 */
class FakeBackend(titles: List<String>) : CallHttpClient {

    private val tasks = linkedMapOf<Long, Task>()
    private var nextId = 1L

    /** The paths the presenters called, in order. */
    val calls = mutableListOf<String>()

    init {
        titles.forEach(::insert)
    }

    /** A task written by someone else (another browser): the presenters only see it when they load again. */
    fun insert(title: String): Task = Task(id = nextId++, title = title).also { tasks[it.id] = it }

    override suspend fun post(path: String, json: String): String {
        calls += path.removePrefix("${Calls.BASE_PATH}/")
        return when (path) {
            TaskListQuery.PATH -> write(ListSerializer(Task.serializer()), tasks.values.toList())
            TaskCreate.PATH -> write(Task.serializer(), insert(read(json, TaskCreate.Request.serializer()).title))
            TaskQuery.PATH -> write(Task.serializer().nullable, tasks[read(json, TaskQuery.Request.serializer()).taskId])
            TaskDoneUpdate.PATH -> {
                val request = read(json, TaskDoneUpdate.Request.serializer())
                write(Task.serializer(), save(request.taskId) { it.copy(done = request.done) })
            }
            TaskTitleUpdate.PATH -> {
                val request = read(json, TaskTitleUpdate.Request.serializer())
                write(Task.serializer(), save(request.taskId) { it.copy(title = request.title) })
            }
            TaskDelete.PATH -> write(Boolean.serializer(), tasks.remove(read(json, TaskDelete.Request.serializer()).taskId) != null)
            else -> throw NotFoundException("No call at $path")
        }
    }

    private fun save(taskId: Long, change: (Task) -> Task): Task {
        val task = tasks[taskId] ?: throw NotFoundException("No task $taskId")
        return change(task).also { tasks[taskId] = it }
    }

    private fun <Q> read(json: String, type: KSerializer<Q>): Q = Calls.json.decodeFromString(type, json)

    private fun <R> write(type: KSerializer<R>, value: R): String = Calls.json.encodeToString(type, value)
}
