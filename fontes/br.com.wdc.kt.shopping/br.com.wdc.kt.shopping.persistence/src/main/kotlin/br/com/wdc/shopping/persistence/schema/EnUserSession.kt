package br.com.wdc.shopping.persistence.schema

import br.com.wdc.shopping.persistence.schema.support.DbTable

class EnUserSession(alias: String) : DbTable(alias) {

    val sessionId = mkVarChar("SESSION_ID", 36, false)
    val userId = mkBigint("USERID", false)
    val userName = mkVarChar("USERNAME", 100, false)
    val refreshToken = mkVarChar("REFRESH_TOKEN", 36, false)
    val expiresAt = mkTimestamp("EXPIRES_AT", false)
    val permissions = mkVarChar("PERMISSIONS", 4000, true)
    val rsaPublicKey = mkVarChar("RSA_PUBLIC_KEY", 2000, false)
    val rsaPrivateKey = mkVarChar("RSA_PRIVATE_KEY", 4000, false)

    private val _fields = listOf(sessionId, userId, userName, refreshToken, expiresAt, permissions, rsaPublicKey, rsaPrivateKey)

    override fun tableName() = "EN_USER_SESSION"
    override fun fields() = _fields

    override fun createTableSql(): String = buildString {
        appendLine("CREATE TABLE IF NOT EXISTS ${tableName()} (")
        appendLine(" ${sessionId.declaration}")
        appendLine(",${userId.declaration}")
        appendLine(",${userName.declaration}")
        appendLine(",${refreshToken.declaration}")
        appendLine(",${expiresAt.declaration}")
        appendLine(",${permissions.declaration}")
        appendLine(",${rsaPublicKey.declaration}")
        appendLine(",${rsaPrivateKey.declaration}")
        appendLine(",CONSTRAINT PK_USER_SESSION PRIMARY KEY (${sessionId.name})")
        appendLine(",CONSTRAINT FK_US_USER FOREIGN KEY (${userId.name}) REFERENCES EN_USER(ID)")
        appendLine(",CONSTRAINT UQ_US_REFRESH UNIQUE (${refreshToken.name})")
        appendLine(")")
    }

    companion object {
        val INSTANCE = EnUserSession("")
    }
}
