package br.com.wdc.shopping.persistence.schema.support

import java.sql.JDBCType

class DbField(
    val name: String,
    val alias: String,
    val path: String,
    val type: JDBCType,
    val ignoreCase: Boolean,
    val lengthOrPrecision: Int,
    val scale: Int,
    val nullable: Boolean,
) {
    val declaration: String = "$name ${computeSqlType(type, ignoreCase, lengthOrPrecision, scale)} ${computeSqlNullable(nullable)}"

    val length: Int get() = lengthOrPrecision
    val precision: Int get() = lengthOrPrecision

    override fun toString(): String = path

    fun asc(): String = "$path ASC"
    fun desc(): String = "$path DESC"

    companion object {
        private fun computeSqlNullable(nullable: Boolean): String =
            if (nullable) "" else "NOT NULL"

        private fun computeSqlType(type: JDBCType, ignoreCase: Boolean, lengthOrPrecision: Int, scale: Int): String =
            when (type) {
                JDBCType.CHAR -> "${computeIgnoreCase("CHAR", ignoreCase)}($lengthOrPrecision)"
                JDBCType.VARCHAR -> "${computeIgnoreCase("VARCHAR", ignoreCase)}($lengthOrPrecision)"
                JDBCType.NUMERIC -> "NUMERIC($lengthOrPrecision,$scale)"
                JDBCType.DECIMAL -> "DECIMAL($lengthOrPrecision,$scale)"
                JDBCType.VARBINARY -> "VARBINARY($lengthOrPrecision)"
                JDBCType.NCHAR -> "${computeIgnoreCase("NCHAR", ignoreCase)}($lengthOrPrecision)"
                JDBCType.NVARCHAR -> "${computeIgnoreCase("NVARCHAR", ignoreCase)}($lengthOrPrecision)"
                JDBCType.BINARY -> "BINARY($lengthOrPrecision)"
                else -> type.name
            }

        private fun computeIgnoreCase(typeName: String, ignoreCase: Boolean): String =
            if (ignoreCase) "${typeName}_IGNORECASE" else typeName
    }
}
