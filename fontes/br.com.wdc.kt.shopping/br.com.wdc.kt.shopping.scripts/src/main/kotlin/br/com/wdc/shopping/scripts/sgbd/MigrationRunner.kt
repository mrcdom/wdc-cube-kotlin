package br.com.wdc.shopping.scripts.sgbd

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.log.getLogger
import br.com.wdc.shopping.persistence.sql.SqlKeywords
import br.com.wdc.shopping.persistence.sql.SqlList
import br.com.wdc.shopping.persistence.sql.SqlUtils
import br.com.wdc.shopping.scripts.sgbd.schema.EnMigrationLog
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant

class MigrationRunner(private val connection: Connection) : SqlKeywords {

    @Throws(SQLException::class)
    fun run(migrationScript: Any): MigrationRunner {
        val scriptName = migrationScript::class.java.simpleName
        val executedSteps = loadExecutedSteps(scriptName)

        val stepMethods = migrationScript::class.java.declaredMethods
            .filter { java.lang.reflect.Modifier.isPublic(it.modifiers) }
            .filter { it.parameterCount == 0 }
            .filter { it.name.lowercase().startsWith(STEP_PREFIX) }
            .sortedBy { extractStepNumber(it) }

        for (method in stepMethods) {
            val stepName = method.name

            if (stepName in executedSteps) {
                LOG.debug("Skipping already executed step: {}.{}", scriptName, stepName)
                continue
            }

            LOG.info("Executing migration step: {}.{}", scriptName, stepName)
            try {
                method.invoke(migrationScript)
                recordStep(scriptName, stepName)
                LOG.info("Completed migration step: {}.{}", scriptName, stepName)
            } catch (e: Exception) {
                throw SQLException("Migration step failed: $scriptName.$stepName", e)
            }
        }

        return this
    }

    private fun loadExecutedSteps(scriptName: String): Set<String> {
        val en = EnMigrationLog.INSTANCE

        val sql = SqlList()
        sql.ln(SELECT)
        val fStepName = sql.strColumn(en.stepName)
        sql.ln(FROM, en.tableName())
        sql.ln(WHERE, en.scriptName, EQUAL, "?")

        val steps = mutableSetOf<String>()
        connection.prepareStatement(sql.toText()).use { ps ->
            ps.setString(1, scriptName)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    fStepName(rs)?.let { steps.add(it) }
                }
            }
        }
        return steps
    }

    private fun recordStep(scriptName: String, stepName: String) {
        val en = EnMigrationLog.INSTANCE
        val nextId = SqlUtils.nextSequence(connection, "SQ_MIGRATION_LOG")

        val sql = SqlList()
        sql.ln(INSERT_INTO, en.tableName(), "(")
        sql.ln(" ", en.id)
        sql.ln(",", en.scriptName)
        sql.ln(",", en.stepName)
        sql.ln(",", en.executedAt)
        sql.ln(")")
        sql.ln(VALUES)
        sql.ln("(?, ?, ?, ?)")

        connection.prepareStatement(sql.toText()).use { ps ->
            ps.setLong(1, nextId)
            ps.setString(2, scriptName)
            ps.setString(3, stepName)
            ps.setTimestamp(4, Timestamp.from(Instant.now()))
            ps.executeUpdate()
        }
    }

    companion object {
        private val LOG = Log.getLogger(MigrationRunner::class.java)
        private const val STEP_PREFIX = "step"

        private fun extractStepNumber(method: java.lang.reflect.Method): Int {
            val name = method.name
            val afterPrefix = name.substring(STEP_PREFIX.length)
            val digits = buildString {
                for (ch in afterPrefix) {
                    if (ch.isDigit()) append(ch) else break
                }
            }
            return if (digits.isEmpty()) Int.MAX_VALUE else digits.toInt()
        }
    }
}
