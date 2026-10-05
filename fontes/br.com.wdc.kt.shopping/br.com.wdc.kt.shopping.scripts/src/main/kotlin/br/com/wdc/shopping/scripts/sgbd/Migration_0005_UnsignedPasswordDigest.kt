package br.com.wdc.shopping.scripts.sgbd

import java.math.BigInteger
import java.sql.Connection
import java.sql.SQLException

/**
 * Regrava os resumos de senha que a carga de demonstração antiga gravou com sinal.
 *
 * O resumo é o MD5 da senha em base 36. A carga o lia como inteiro **com sinal**; a aplicação
 * (`PasswordUtil.hashPassword`) o calcula **sem sinal**. Os dois coincidem quando o primeiro bit do MD5 é 0;
 * quando é 1, a carga gravava um número negativo e o usuário nunca conseguia autenticar.
 */
class Migration_0005_UnsignedPasswordDigest(private val connection: Connection) {

    @Throws(SQLException::class)
    fun step01_rewriteSignedDigests() {
        val signed = HashMap<Long, String>()
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT ID, PASSWORD FROM EN_USER").use { rs ->
                while (rs.next()) {
                    val digest = rs.getString(2)?.trim() ?: continue
                    // só o que tem a forma de um resumo com sinal: '-' seguido de dígitos de base 36
                    if (SIGNED_DIGEST.matches(digest)) {
                        signed[rs.getLong(1)] = digest
                    }
                }
            }
        }
        connection.prepareStatement("UPDATE EN_USER SET PASSWORD = ? WHERE ID = ?").use { ps ->
            for ((id, digest) in signed) {
                // o mesmo padrão de 128 bits, lido sem sinal
                ps.setString(1, BigInteger(digest, 36).add(TWO_POW_128).toString(36))
                ps.setLong(2, id)
                ps.executeUpdate()
            }
        }
    }

    private companion object {
        val SIGNED_DIGEST = Regex("-[0-9a-z]{1,25}")
        val TWO_POW_128: BigInteger = BigInteger.ONE.shiftLeft(128)
    }
}
