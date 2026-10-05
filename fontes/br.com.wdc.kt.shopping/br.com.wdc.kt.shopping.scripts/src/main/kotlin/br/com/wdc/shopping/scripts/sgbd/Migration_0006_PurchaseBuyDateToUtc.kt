package br.com.wdc.shopping.scripts.sgbd

import java.sql.Connection
import java.sql.SQLException
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Passa a data das compras já gravadas para UTC.
 *
 * `BUYDATE` é TIMESTAMP sem fuso. A persistência antiga entregava ao driver um valor com fuso, e o banco o
 * guardava na hora local da máquina; a atual guarda — e lê — o instante em UTC. Sem esta conversão, as compras
 * antigas apareceriam deslocadas pelo fuso da máquina.
 *
 * A conversão usa o fuso desta JVM, que se supõe ser o mesmo de quem gravou os dados.
 */
class Migration_0006_PurchaseBuyDateToUtc(
    private val connection: Connection,
    private val writtenIn: ZoneId = ZoneId.systemDefault(),
) {

    @Throws(SQLException::class)
    fun step01_localWallTimeToUtc() {
        val dates = HashMap<Long, LocalDateTime>()
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT ID, BUYDATE FROM EN_PURCHASE").use { rs ->
                while (rs.next()) {
                    val local = rs.getObject(2, LocalDateTime::class.java) ?: continue
                    dates[rs.getLong(1)] = local
                }
            }
        }
        connection.prepareStatement("UPDATE EN_PURCHASE SET BUYDATE = ? WHERE ID = ?").use { ps ->
            for ((id, local) in dates) {
                val utc = local.atZone(writtenIn).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()
                if (utc != local) {
                    ps.setObject(1, utc)
                    ps.setLong(2, id)
                    ps.executeUpdate()
                }
            }
        }
    }
}
