package com.example.tasks.data

import br.com.wdc.framework.commons.util.AtomicRef

/**
 * Where the backend keeps the tasks: only the backend's actions (`…Action`, one per call) use it, reading [BEAN] when
 * they run (the backend's entry point, or a test, registers the implementation). The frontend never sees it: its calls
 * go to the backend over HTTP.
 *
 * A real application uses the framework's contracts instead (`ReadOnlyRepository`/`Repository` of `framework-domain`,
 * with criteria and projections, on a database); the WDC Store shows them.
 */
interface TaskRepository {

    companion object {
        val BEAN = AtomicRef<TaskRepository>()
    }

    suspend fun fetchAll(): List<Task>

    suspend fun fetchById(id: Long): Task?

    suspend fun insert(title: String): Task

    suspend fun update(task: Task): Boolean

    suspend fun delete(id: Long): Boolean
}
