package br.com.wdc.shopping.persistence.schema

import br.com.wdc.shopping.persistence.schema.support.DbTable

class EnUserIntentSecret(alias: String) : DbTable(alias) {

    val userId = mkBigint("USERID", false)
    val secret = mkVarChar("SECRET", 64, false)

    private val _fields = listOf(userId, secret)

    override fun tableName() = "EN_USER_INTENT_SECRET"
    override fun fields() = _fields

    override fun createTableSql(): String = buildString {
        appendLine("CREATE TABLE IF NOT EXISTS ${tableName()} (")
        appendLine(" ${userId.declaration}")
        appendLine(",${secret.declaration}")
        appendLine(",CONSTRAINT PK_USER_INTENT_SECRET PRIMARY KEY (${userId.name})")
        appendLine(",CONSTRAINT FK_UIS_USER FOREIGN KEY (${userId.name}) REFERENCES EN_USER(ID)")
        appendLine(")")
    }

    companion object {
        val INSTANCE = EnUserIntentSecret("")
    }
}
