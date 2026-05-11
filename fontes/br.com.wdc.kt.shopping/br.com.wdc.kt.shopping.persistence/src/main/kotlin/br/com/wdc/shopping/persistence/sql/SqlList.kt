package br.com.wdc.shopping.persistence.sql

import java.math.BigDecimal
import java.math.BigInteger
import java.sql.ResultSet

class SqlList : ArrayList<String> {

    constructor() : super()
    constructor(initialCapacity: Int) : super(initialCapacity)
    constructor(c: Collection<String>) : super(c)

    fun ln(vararg items: Any?): SqlList {
        add(items.joinToString(" "))
        return this
    }

    // :: Projection

    private var projectionCount: Int = 0

    private fun projectionToLn(columnIndex: Int, vararg items: Any?): String {
        val prefix = if (columnIndex == 1) " " else ","
        return "$prefix${items.joinToString(" ")}"
    }

    fun field(vararg items: Any?): SqlList {
        val columnIndex = ++projectionCount
        ln(projectionToLn(columnIndex, *items))
        return this
    }

    fun field(field: Any?): SqlList {
        val columnIndex = ++projectionCount
        ln(projectionToLn(columnIndex, field))
        return this
    }

    fun bitColumn(vararg items: Any?): (ResultSet) -> Boolean? {
        val columnIndex = ++projectionCount
        ln(projectionToLn(columnIndex, *items))
        return { rs ->
            val v = rs.getBoolean(columnIndex)
            if (rs.wasNull()) null else v
        }
    }

    fun i08Column(vararg items: Any?): (ResultSet) -> Byte? {
        val columnIndex = ++projectionCount
        ln(projectionToLn(columnIndex, *items))
        return { rs ->
            val v = rs.getByte(columnIndex)
            if (rs.wasNull()) null else v
        }
    }

    fun i16Column(vararg items: Any?): (ResultSet) -> Short? {
        val columnIndex = ++projectionCount
        ln(projectionToLn(columnIndex, *items))
        return { rs ->
            val v = rs.getShort(columnIndex)
            if (rs.wasNull()) null else v
        }
    }

    fun i32Column(vararg items: Any?): (ResultSet) -> Int? {
        val columnIndex = ++projectionCount
        ln(projectionToLn(columnIndex, *items))
        return { rs ->
            val v = rs.getInt(columnIndex)
            if (rs.wasNull()) null else v
        }
    }

    fun i64Column(vararg items: Any?): (ResultSet) -> Long? {
        val columnIndex = ++projectionCount
        ln(projectionToLn(columnIndex, *items))
        return { rs ->
            val v = rs.getLong(columnIndex)
            if (rs.wasNull()) null else v
        }
    }

    fun intColumn(vararg items: Any?): (ResultSet) -> BigInteger? {
        val columnIndex = ++projectionCount
        ln(projectionToLn(columnIndex, *items))
        return { rs ->
            rs.getBigDecimal(columnIndex)?.toBigInteger()
        }
    }

    fun f32Column(vararg items: Any?): (ResultSet) -> Float? {
        val columnIndex = ++projectionCount
        ln(projectionToLn(columnIndex, *items))
        return { rs ->
            val v = rs.getFloat(columnIndex)
            if (rs.wasNull()) null else v
        }
    }

    fun f64Column(vararg items: Any?): (ResultSet) -> Double? {
        val columnIndex = ++projectionCount
        ln(projectionToLn(columnIndex, *items))
        return { rs ->
            val v = rs.getDouble(columnIndex)
            if (rs.wasNull()) null else v
        }
    }

    fun decColumn(vararg items: Any?): (ResultSet) -> BigDecimal? {
        val columnIndex = ++projectionCount
        ln(projectionToLn(columnIndex, *items))
        return { rs -> rs.getBigDecimal(columnIndex) }
    }

    fun binColumn(vararg items: Any?): (ResultSet) -> ByteArray? {
        val columnIndex = ++projectionCount
        ln(projectionToLn(columnIndex, *items))
        return { rs -> rs.getBytes(columnIndex) }
    }

    fun strColumn(vararg items: Any?): (ResultSet) -> String? {
        val columnIndex = ++projectionCount
        ln(projectionToLn(columnIndex, *items))
        return { rs -> rs.getString(columnIndex) }
    }

    fun dteColumn(vararg items: Any?): (ResultSet) -> java.sql.Date? {
        val columnIndex = ++projectionCount
        ln(projectionToLn(columnIndex, *items))
        return { rs -> rs.getDate(columnIndex) }
    }

    fun tmeColumn(vararg items: Any?): (ResultSet) -> java.sql.Time? {
        val columnIndex = ++projectionCount
        ln(projectionToLn(columnIndex, *items))
        return { rs -> rs.getTime(columnIndex) }
    }

    fun dttColumn(vararg items: Any?): (ResultSet) -> java.sql.Timestamp? {
        val columnIndex = ++projectionCount
        ln(projectionToLn(columnIndex, *items))
        return { rs -> rs.getTimestamp(columnIndex) }
    }

    fun clobColumn(vararg items: Any?): (ResultSet) -> java.sql.Clob? {
        val columnIndex = ++projectionCount
        ln(projectionToLn(columnIndex, *items))
        return { rs -> rs.getClob(columnIndex) }
    }

    fun blobColumn(vararg items: Any?): (ResultSet) -> java.sql.Blob? {
        val columnIndex = ++projectionCount
        ln(projectionToLn(columnIndex, *items))
        return { rs -> rs.getBlob(columnIndex) }
    }

    fun toText(): String = toText("")

    fun toText(identLength: Int): String {
        if (isEmpty()) return ""
        val ident = if (identLength > 0) " ".repeat(identLength) else ""
        return toText(ident)
    }

    fun toText(ident: String): String {
        val sb = StringBuilder(size * 160)
        var br = ""
        for (line in this) {
            if (line.isBlank()) continue
            sb.append(br)
            sb.append(ident)
            sb.append(line)
            br = "\n"
        }
        return sb.toString()
    }

    companion object {
        fun create(builder: (SqlList) -> Unit): SqlList {
            val sql = SqlList()
            builder(sql)
            return sql
        }
    }
}
