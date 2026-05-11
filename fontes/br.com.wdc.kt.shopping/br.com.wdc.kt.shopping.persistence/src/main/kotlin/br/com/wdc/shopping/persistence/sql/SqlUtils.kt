package br.com.wdc.shopping.persistence.sql

import br.com.wdc.framework.commons.util.Defer
import br.com.wdc.shopping.persistence.schema.support.DbField
import java.sql.Connection
import java.sql.JDBCType
import java.sql.SQLException

object SqlUtils {

    fun comma(): () -> String {
        var first = true
        return {
            if (first) { first = false; " " } else ","
        }
    }

    fun nextSequence(connection: Connection, sequenceName: String): Long {
        Defer().use { defer ->
            val stmt = connection.createStatement()
            defer.push { stmt.close() }

            val rs = stmt.executeQuery("SELECT NEXT VALUE FOR $sequenceName")
            defer.push { rs.close() }
            if (rs.next()) {
                return rs.getLong(1)
            }
            throw SQLException("No value returned from sequence")
        }
    }

    fun alterSequence(connection: Connection, name: String, value: Long) {
        connection.createStatement().use { stmt ->
            stmt.execute("ALTER SEQUENCE $name RESTART WITH $value")
        }
    }

    fun toJsonField(fields: List<DbField>): String {
        if (fields.isEmpty()) return "null"

        val sb = StringBuilder(fields.size * 30)
        sb.append("JSON_OBJECT(")

        for ((index, field) in fields.withIndex()) {
            if (index > 0) sb.append(',')
            sb.append('\'')
            sb.append(field.name)
            sb.append("': ")

            when (field.type) {
                JDBCType.BINARY -> {
                    sb.append("RAWTOHEX(")
                    sb.append(field.path)
                    sb.append(")")
                }
                JDBCType.VARCHAR -> {
                    sb.append("CAST(")
                    sb.append(field.path)
                    sb.append(" AS VARCHAR)")
                }
                else -> sb.append(field.path)
            }
        }
        sb.append(')')

        return sb.toString()
    }
}
