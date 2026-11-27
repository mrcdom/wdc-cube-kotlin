package com.example.tasks.persistence

import org.jooq.Identity
import org.jooq.Name
import org.jooq.Record
import org.jooq.Table
import org.jooq.TableField
import org.jooq.UniqueKey
import org.jooq.impl.DSL
import org.jooq.impl.Internal
import org.jooq.impl.SQLDataType
import org.jooq.impl.TableImpl

/**
 * The `TASK` table, as jOOQ sees it: written by hand, in the format jOOQ's code generator produces. With a handful of
 * tables this is shorter than a generator; an application with many generates them from its schema (the WDC Store
 * does, with `:store-scripts:generateJooqSchema`). It has to match [TaskSchema.CREATE_TASK].
 */
class TaskTable(alias: Name = DSL.name("TASK"), aliased: Table<Record>? = null) :
    TableImpl<Record>(alias, null, aliased) {

    companion object {
        val TASK = TaskTable()
    }

    val ID: TableField<Record, Long?> =
        createField(DSL.name("ID"), SQLDataType.BIGINT.nullable(false).generatedByDefaultAsIdentity(), this)
    val TITLE: TableField<Record, String?> =
        createField(DSL.name("TITLE"), SQLDataType.VARCHAR(TaskSchema.TITLE_LENGTH).nullable(false), this)
    val DONE: TableField<Record, Boolean?> =
        createField(DSL.name("DONE"), SQLDataType.BOOLEAN.nullable(false), this)

    // the database makes the id: an insert leaves it out and reads it back
    override fun getIdentity(): Identity<Record, Long?> = Internal.createIdentity(this, ID)

    override fun getPrimaryKey(): UniqueKey<Record> =
        Internal.createUniqueKey(this, DSL.name("PK_TASK"), arrayOf<TableField<Record, *>>(ID), true)

    override fun `as`(alias: String): TaskTable = TaskTable(DSL.name(alias), this)
}
