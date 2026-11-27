package com.example.tasks.data

import br.com.wdc.framework.domain.criteria.ComparableCriterion
import br.com.wdc.framework.domain.criteria.Criteria
import br.com.wdc.framework.domain.criteria.Criterion
import br.com.wdc.framework.domain.criteria.TextCriterion

/**
 * Which tasks: each field is a criterion (`eq`, `isIn`, `like`…), and a criterion left unset does not filter. No
 * criterion at all means every task. [projection] says which fields to bring (all of them when `null`).
 */
class TaskCriteria : Criteria {

    var projection: TaskEntity? = null
        private set

    fun withProjection(projection: TaskEntity?) = apply { this.projection = projection }

    val taskId = ComparableCriterion<TaskCriteria, Long>(this, "taskId")

    fun withTaskId(value: Long?): TaskCriteria = if (value == null) this else taskId.eq(value)

    val title = TextCriterion(this, "title")

    val done = Criterion<TaskCriteria, Boolean>(this, "done")

    fun withDone(value: Boolean?): TaskCriteria = if (value == null) this else done.eq(value)

    override fun criterions(): List<Criterion<*, *>> = listOf(taskId, title, done)
}
