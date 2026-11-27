package com.example.tasks.persistence

import br.com.wdc.framework.domain.exception.InvalidRequestException
import br.com.wdc.framework.jooq.CriterionTranslator
import br.com.wdc.framework.jooq.JsonQuery
import br.com.wdc.framework.jooq.JsonQueryBuilder
import com.example.tasks.data.TaskCriteria
import com.example.tasks.data.TaskEntity
import com.example.tasks.data.TaskRepository
import com.example.tasks.persistence.TaskTable.Companion.TASK
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.SortField

/**
 * The tasks in the database, through jOOQ. Reads go through [QUERY], which turns a projection into one `SELECT` that
 * returns each row as JSON, and fills [TaskEntity] from it; writes are jOOQ statements. Every statement runs on the
 * connection of the current transaction, when there is one ([TaskDatabase] wires the `DSLContext` that way): the
 * repository never opens, commits or rolls back.
 */
class TaskRepositoryImpl : TaskRepository {

    private val dsl: DSLContext get() = TaskDatabase.DSL_CONTEXT.get()

    override suspend fun count(criteria: TaskCriteria): Int =
        dsl.selectCount().from(TASK).where(conditions(TASK, criteria)).fetchOne()!!.value1()

    override suspend fun fetch(criteria: TaskCriteria, offset: Int, limit: Int): List<TaskEntity> =
        QUERY.fetchToList(criteria.projection ?: ownFieldsProjection()) { table, query ->
            val step = query.where(conditions(table, criteria))
            step.orderBy(orderingOf(table, criteria))
            if (limit > 0) step.limit(limit)
            if (offset > 0) step.offset(offset)
        }

    /** Writes the fields the projection marks (all with a value, without one) and reads the new id back into [bean]. */
    override suspend fun insert(bean: TaskEntity, projection: TaskEntity?): Boolean = QUERY.insert(bean, projection)

    /**
     * Writes the fields that changed: those marked by [projection] (all of them, without one) whose value differs from
     * [oldBean]'s. Without either, the fields of [newBean] that have a value.
     */
    override suspend fun update(newBean: TaskEntity, oldBean: TaskEntity?, projection: TaskEntity?): Boolean {
        val id = newBean.id ?: throw InvalidRequestException("A task update needs the id")
        val marked = projection ?: ownFieldsProjection()
        val base = oldBean ?: if (projection == null) TaskEntity() else null

        val step = dsl.update(TASK).set(TASK.ID, id)
        var hasChanges = false
        if (changed(newBean, base, marked) { it.title }) {
            step.set(TASK.TITLE, newBean.title)
            hasChanges = true
        }
        if (changed(newBean, base, marked) { it.done }) {
            step.set(TASK.DONE, newBean.done)
            hasChanges = true
        }
        if (!hasChanges) return false
        return step.where(TASK.ID.eq(id)).execute() > 0
    }

    /** Deletes the tasks the criteria select; criteria without any criterion are refused (no `DELETE` of everything). */
    override suspend fun delete(criteria: TaskCriteria): Int {
        if (criteria.criterions().none { it.isSet() }) throw InvalidRequestException("A task delete needs a criterion")
        return dsl.deleteFrom(TASK).where(conditions(TASK, criteria)).execute()
    }

    /** A field goes in the statement when the projection marks it and its value is not [oldBean]'s. */
    private fun <V> changed(newBean: TaskEntity, oldBean: TaskEntity?, projection: TaskEntity, getter: (TaskEntity) -> V?) =
        getter(projection) != null && (oldBean == null || getter(newBean) != getter(oldBean))

    companion object {

        /** How a task is read: each field of the entity, its column. */
        val QUERY: JsonQuery<TaskEntity, TaskTable> = JsonQueryBuilder<TaskEntity, TaskTable>()
            .setAlias("t")
            .setBeanFactory(::TaskEntity)
            .setTableFactory { alias -> TASK.`as`(alias) }
            .setDSLContextSupplier { TaskDatabase.DSL_CONTEXT.get() }
            .setOrdering(::orderingOf)
            .addI64("id", { it.id }, { b, v -> b.id = v }, { it.ID })
            .addStr("title", { it.title }, { b, v -> b.title = v }, { it.TITLE })
            .addBit("done", { it.done }, { b, v -> b.done = v }, { it.DONE })
            .build()

        /** In the order they were created. */
        private fun orderingOf(table: TaskTable, @Suppress("UNUSED_PARAMETER") criteria: Any?): List<SortField<*>> =
            listOf(table.ID.asc())

        private fun conditions(table: TaskTable, criteria: TaskCriteria): Condition = CriterionTranslator.and(
            listOf(
                CriterionTranslator.translate(table.ID, criteria.taskId),
                CriterionTranslator.translate(table.TITLE, criteria.title),
                CriterionTranslator.translate(table.DONE, criteria.done),
            ),
        )
    }
}
