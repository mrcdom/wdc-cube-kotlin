package br.com.wdc.shopping.scripts.sgbd

import java.sql.Connection
import java.sql.SQLException

/**
 * As tabelas de segurança, para os bancos criados antes de elas existirem. Hoje o `DBCreate` já as cria, e
 * estes passos não encontram o que fazer.
 */
class Migration_0003_CreateSecurityTables(private val connection: Connection) {

    @Throws(SQLException::class)
    fun step01_createUserIntentSecretTable() {
        connection.createStatement().use { stmt ->
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS EN_USER_INTENT_SECRET (
                    USERID BIGINT NOT NULL,
                    SECRET VARCHAR(64) NOT NULL,
                    CONSTRAINT PK_USER_INTENT_SECRET PRIMARY KEY (USERID),
                    CONSTRAINT FK_UIS_USER FOREIGN KEY (USERID) REFERENCES EN_USER(ID)
                )""",
            )
        }
    }

    @Throws(SQLException::class)
    fun step02_createUserSessionTable() {
        connection.createStatement().use { stmt ->
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS EN_USER_SESSION (
                    SESSION_ID VARCHAR(36) NOT NULL,
                    USERID BIGINT NOT NULL,
                    USERNAME VARCHAR(100) NOT NULL,
                    REFRESH_TOKEN VARCHAR(36) NOT NULL,
                    EXPIRES_AT TIMESTAMP NOT NULL,
                    PERMISSIONS VARCHAR(4000),
                    RSA_PUBLIC_KEY VARCHAR(2000) NOT NULL,
                    RSA_PRIVATE_KEY VARCHAR(4000) NOT NULL,
                    CONSTRAINT PK_USER_SESSION PRIMARY KEY (SESSION_ID),
                    CONSTRAINT FK_US_USER FOREIGN KEY (USERID) REFERENCES EN_USER(ID),
                    CONSTRAINT UQ_US_REFRESH UNIQUE (REFRESH_TOKEN)
                )""",
            )
        }
    }
}
