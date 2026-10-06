package br.com.wdc.shopping.scripts.sgbd

import br.com.wdc.framework.commons.log.Log
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import org.jooq.SQLDialect

class MigrationRunner(private val connection: Connection) {

    private val nextIdSql =
        if (DBCreate.detectDialect(connection) == SQLDialect.POSTGRES) "SELECT nextval('sq_migration_log')"
        else "SELECT NEXT VALUE FOR SQ_MIGRATION_LOG"


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
        val steps = mutableSetOf<String>()
        connection.prepareStatement("SELECT STEP_NAME FROM EN_MIGRATION_LOG WHERE SCRIPT_NAME = ?").use { ps ->
            ps.setString(1, scriptName)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    rs.getString(1)?.let { steps.add(it) }
                }
            }
        }
        return steps
    }

    private fun recordStep(scriptName: String, stepName: String) {
        val nextId = connection.createStatement().use { stmt ->
            stmt.executeQuery(nextIdSql).use { rs ->
                if (!rs.next()) throw SQLException("No value returned from sequence")
                rs.getLong(1)
            }
        }
        connection.prepareStatement(
            "INSERT INTO EN_MIGRATION_LOG (ID, SCRIPT_NAME, STEP_NAME, EXECUTED_AT) VALUES (?, ?, ?, ?)"
        ).use { ps ->
            ps.setLong(1, nextId)
            ps.setString(2, scriptName)
            ps.setString(3, stepName)
            ps.setTimestamp(4, Timestamp.from(Instant.now()))
            ps.executeUpdate()
        }
    }

    companion object {
        private val LOG = Log.getLogger("MigrationRunner")
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
