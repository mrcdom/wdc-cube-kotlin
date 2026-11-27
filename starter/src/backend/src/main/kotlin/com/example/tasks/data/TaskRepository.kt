package com.example.tasks.data

import br.com.wdc.framework.commons.util.AtomicRef
import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.framework.domain.repository.Repository

/**
 * Where the backend keeps the tasks: the framework's repository contract (`count`, `fetch`, `fetchById`, `insert`,
 * `update`, `delete`, with [TaskCriteria] and projections). Only the backend's actions (`…Action`, one per call) use
 * it, reading [BEAN] when they run; the backend's entry point, or a test, registers the implementation
 * (`TaskDatabase.open`). The frontend never sees it: its calls go to the backend over HTTP.
 *
 * The contract says nothing about the database: the implementation (`persistence/TaskRepositoryImpl`) is the one
 * that knows SQL.
 */
interface TaskRepository : Repository<TaskEntity, TaskCriteria, Long> {

    companion object {
        val BEAN = AtomicRef<TaskRepository>()
    }

    override fun ownFieldsProjection(): TaskEntity {
        val pv = ProjectionValues
        return TaskEntity().apply {
            id = pv.i64
            title = pv.str
            done = pv.bool
        }
    }

    override suspend fun fetchById(id: Long, projection: TaskEntity?): TaskEntity? {
        val criteria = TaskCriteria()
            .withTaskId(id)
            .withProjection(projection ?: ownFieldsProjection())
        return fetch(criteria = criteria, offset = 0, limit = 1).firstOrNull()
    }
}
