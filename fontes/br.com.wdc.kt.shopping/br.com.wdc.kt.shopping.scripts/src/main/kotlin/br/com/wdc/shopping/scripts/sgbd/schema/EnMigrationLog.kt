package br.com.wdc.shopping.scripts.sgbd.schema

import br.com.wdc.shopping.persistence.schema.support.DbField
import br.com.wdc.shopping.persistence.schema.support.DbTable

class EnMigrationLog(alias: String) : DbTable(alias) {

    val id = mkBigint("ID", false)
    val scriptName = mkVarChar("SCRIPT_NAME", 255, false)
    val stepName = mkVarChar("STEP_NAME", 255, false)
    val executedAt = mkTimestamp("EXECUTED_AT", false)

    private val _fields = listOf(id, scriptName, stepName, executedAt)

    override fun tableName() = "EN_MIGRATION_LOG"
    override fun fields() = _fields

    override fun createTableSql(): String = buildString {
        appendLine("CREATE TABLE IF NOT EXISTS ${tableName()} (")
        appendLine(" ${id.declaration}")
        appendLine(",${scriptName.declaration}")
        appendLine(",${stepName.declaration}")
        appendLine(",${executedAt.declaration}")
        appendLine(",CONSTRAINT PK_MIGRATION_LOG PRIMARY KEY (${id.name})")
        appendLine(")")
    }

    override fun createSequenceSql() = "CREATE SEQUENCE IF NOT EXISTS SQ_MIGRATION_LOG START WITH 1 INCREMENT BY 1"

    companion object {
        @JvmField
        val INSTANCE = EnMigrationLog("")
    }
}
