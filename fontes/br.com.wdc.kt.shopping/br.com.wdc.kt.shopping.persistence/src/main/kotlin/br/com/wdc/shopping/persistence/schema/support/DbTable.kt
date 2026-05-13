package br.com.wdc.shopping.persistence.schema.support

import java.sql.JDBCType

abstract class DbTable(
    val alias: String,
) {
    val basePath: String = if (alias.isNotBlank()) "$alias." else ""

    abstract fun tableName(): String
    abstract fun fields(): List<DbField>
    abstract fun createTableSql(): String

    open fun createSequenceSql(): String =
        throw UnsupportedOperationException()

    fun tableRef(): String =
        if (alias.isNotBlank()) "${tableName()} $alias" else tableName()

    protected fun mkTinyint(name: String, nullable: Boolean) =
        DbField(name, alias, basePath + name, JDBCType.TINYINT, false, 0, 0, nullable)

    protected fun mkSmallint(name: String, nullable: Boolean) =
        DbField(name, alias, basePath + name, JDBCType.SMALLINT, false, 0, 0, nullable)

    protected fun mkInt(name: String, nullable: Boolean) =
        DbField(name, alias, basePath + name, JDBCType.INTEGER, false, 0, 0, nullable)

    protected fun mkBigint(name: String, nullable: Boolean) =
        DbField(name, alias, basePath + name, JDBCType.BIGINT, false, 0, 0, nullable)

    protected fun mkNumeric(name: String, precision: Int, scale: Int, nullable: Boolean) =
        DbField(name, alias, basePath + name, JDBCType.NUMERIC, false, precision, scale, nullable)

    protected fun mkDecimal(name: String, precision: Int, scale: Int, nullable: Boolean) =
        DbField(name, alias, basePath + name, JDBCType.DECIMAL, false, precision, scale, nullable)

    protected fun mkFloat(name: String, nullable: Boolean) =
        DbField(name, alias, basePath + name, JDBCType.FLOAT, false, 0, 0, nullable)

    protected fun mkDouble(name: String, nullable: Boolean) =
        DbField(name, alias, basePath + name, JDBCType.DOUBLE, false, 0, 0, nullable)

    protected fun mkBoolean(name: String, nullable: Boolean) =
        DbField(name, alias, basePath + name, JDBCType.BOOLEAN, false, 0, 0, nullable)

    protected fun mkChar(name: String, length: Int, nullable: Boolean) =
        DbField(name, alias, basePath + name, JDBCType.CHAR, false, length, 0, nullable)

    protected fun mkCharIgnoreCase(name: String, length: Int, nullable: Boolean) =
        DbField(name, alias, basePath + name, JDBCType.CHAR, true, length, 0, nullable)

    protected fun mkVarChar(name: String, length: Int, nullable: Boolean) =
        DbField(name, alias, basePath + name, JDBCType.VARCHAR, false, length, 0, nullable)

    protected fun mkVarCharIgnoreCase(name: String, length: Int, nullable: Boolean) =
        DbField(name, alias, basePath + name, JDBCType.VARCHAR, true, length, 0, nullable)

    protected fun mkNChar(name: String, length: Int, nullable: Boolean) =
        DbField(name, alias, basePath + name, JDBCType.NCHAR, false, length, 0, nullable)

    protected fun mkNCharIgnoreCase(name: String, length: Int, nullable: Boolean) =
        DbField(name, alias, basePath + name, JDBCType.NCHAR, true, length, 0, nullable)

    protected fun mkNVarChar(name: String, length: Int, nullable: Boolean) =
        DbField(name, alias, basePath + name, JDBCType.NVARCHAR, false, length, 0, nullable)

    protected fun mkNVarCharIgnoreCase(name: String, length: Int, nullable: Boolean) =
        DbField(name, alias, basePath + name, JDBCType.NVARCHAR, true, length, 0, nullable)

    protected fun mkBinary(name: String, length: Int, nullable: Boolean) =
        DbField(name, alias, basePath + name, JDBCType.BINARY, false, length, 0, nullable)

    protected fun mkVarBinary(name: String, length: Int, nullable: Boolean) =
        DbField(name, alias, basePath + name, JDBCType.VARBINARY, false, length, 0, nullable)

    protected fun mkDate(name: String, nullable: Boolean) =
        DbField(name, alias, basePath + name, JDBCType.DATE, false, 0, 0, nullable)

    protected fun mkTime(name: String, nullable: Boolean) =
        DbField(name, alias, basePath + name, JDBCType.TIME, false, 0, 0, nullable)

    protected fun mkTimestamp(name: String, nullable: Boolean) =
        DbField(name, alias, basePath + name, JDBCType.TIMESTAMP, false, 0, 0, nullable)

    protected fun mkClob(name: String, nullable: Boolean) =
        DbField(name, alias, basePath + name, JDBCType.CLOB, false, 0, 0, nullable)

    protected fun mkBlob(name: String, nullable: Boolean) =
        DbField(name, alias, basePath + name, JDBCType.BLOB, false, 0, 0, nullable)
}
