package br.com.wdc.shopping.persistence.sql

interface SqlKeywords {
    companion object : SqlKeywords

    val WITH get() = "WITH"
    val SELECT get() = "SELECT"
    val UPDATE get() = "UPDATE"
    val DELETE get() = "DELETE"
    val INSERT_INTO get() = "INSERT INTO"
    val VALUES get() = "VALUES"
    val SET get() = "SET"
    val COUNT get() = "COUNT"
    val DISTINCT get() = "DISTINCT"
    val FROM get() = "FROM"
    val JOIN get() = "JOIN"
    val WHERE get() = "WHERE"
    val WHERE_TRUE get() = "WHERE 1=1"
    val ON get() = "ON"
    val IN get() = "IN"
    val BETWEEN get() = "BETWEEN"
    val AND get() = "AND"
    val OR get() = "OR"
    val LIKE get() = "LIKE"
    val ORDER_BY get() = "ORDER BY"
    val ASC get() = "ASC"
    val DESC get() = "DESC"
    val HAVING get() = "HAVING"
    val LIMIT get() = "LIMIT"
    val OFFSET get() = "OFFSET"
    val NOT get() = "NOT"
    val AS get() = "AS"
    val UNION get() = "UNION"
    val UNION_ALL get() = "UNION ALL"

    val EQUAL get() = "="
    val DIFFERENT get() = "<>"
    val GREATER_THAN get() = ">"
    val GREATER_OR_EQUAL get() = ">="
    val LESS_THAN get() = "<"
    val LESS_OR_EQUAL get() = "<="

    fun COUNT(vararg items: Any?): String = "COUNT(${items.joinToString(" ")})"

    fun IN(vararg items: Any?): String = "IN(${items.joinToString(" ")})"

    fun IN(sql: SqlList): String = "IN(\n${sql.toText("  ")}  )"

    fun IN(builder: (SqlList) -> Unit): String = "IN(\n${SqlList.create(builder).toText("    ")})"

    fun ON(vararg items: Any?): String = "ON(${items.joinToString(" ")})"

    fun BETWEEN(a: Any?, b: Any?): String = "BETWEEN $a AND $b"

    fun EXISTS(sql: SqlList): String = "EXISTS(\n${sql.toText("    ")})"

    fun EXISTS(builder: (SqlList) -> Unit): String = "EXISTS(\n${SqlList.create(builder).toText("    ")})"

    fun ORDER_BY(vararg items: Any?): String = "ORDER BY ${items.joinToString(", ")}"
}
