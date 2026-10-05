package br.com.wdc.shopping.scripts.sgbd

import java.sql.Connection
import java.sql.SQLException

/**
 * Descarta as sessões gravadas com a expiração na hora local da máquina.
 *
 * `EXPIRES_AT` é TIMESTAMP sem fuso e passou a guardar o instante em UTC, como as demais datas. As sessões
 * antigas expirariam na hora errada; como sessão é estado descartável, saem — o usuário autentica de novo.
 */
class Migration_0007_SessionExpiryToUtc(private val connection: Connection) {

    @Throws(SQLException::class)
    fun step01_dropLocalTimeSessions() {
        connection.createStatement().use { stmt ->
            stmt.execute("DELETE FROM EN_USER_SESSION")
        }
    }
}
