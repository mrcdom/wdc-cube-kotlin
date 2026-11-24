package br.com.wdc.store.scripts.sgbd

import java.sql.Connection
import java.sql.SQLException
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Converts the date of the purchases already stored to UTC.
 *
 * `BUYDATE` is a TIMESTAMP without time zone. The old persistence handed the driver a value with a time zone, and
 * the database stored it in the machine's local time; the current one stores — and reads — the instant in UTC.
 * Without this conversion, old purchases would show up shifted by the machine's time zone.
 *
 * The conversion uses this JVM's time zone, which is assumed to be the same as the one that stored the data.
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
