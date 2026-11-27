package com.example.tasks.data

import br.com.wdc.framework.domain.codec.KeyedEntity

/**
 * A task as the repository keeps it: every field optional, because the same class also serves as a projection (a
 * field with a value = "bring it") and as a partial update (only the fields with a value are written). The calls
 * answer with [Task], the shared model: [toTask].
 */
class TaskEntity : KeyedEntity {
    var id: Long? = null
    var title: String? = null
    var done: Boolean? = null

    override fun key(): Any? = id

    /** The shared model, from a task read with all its fields. */
    fun toTask(): Task = Task(
        id = checkNotNull(id) { "a task read without its id" },
        title = title.orEmpty(),
        done = done ?: false,
    )
}
