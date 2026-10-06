package br.com.wdc.shopping.scripts.sgbd

import java.sql.Connection
import java.sql.SQLException
import org.jooq.SQLDialect

/**
 * Leva os bancos criados antes da adoção do jOOQ ao esquema atual de [DBCreate]:
 * a imagem do produto deixa de ser `BINARY` de tamanho fixo e entram os índices das ordenações.
 */
class Migration_0004_ImageVarbinaryAndOrderingIndexes(private val connection: Connection) {

    private val h2 = DBCreate.detectDialect(connection) == SQLDialect.H2

    @Throws(SQLException::class)
    fun step01_imageToVarbinary() {
        // só o H2 teve a coluna como BINARY; no PostgreSQL ela já nasce BYTEA
        if (!h2) return
        connection.createStatement().use { stmt ->
            stmt.execute("ALTER TABLE EN_PRODUCT ALTER COLUMN IMAGE SET DATA TYPE VARBINARY($LEGACY_IMAGE_SIZE)")
        }
    }

    /**
     * Enquanto a coluna era `BINARY(1000000)`, o H2 completava cada imagem com zeros até o tamanho da coluna.
     * Remove esse preenchimento das linhas que o têm (reconhecidas por terem exatamente o tamanho da coluna).
     */
    @Throws(SQLException::class)
    fun step02_trimPaddedImages() {
        if (!h2) return
        val padded = HashMap<Long, ByteArray>()
        connection.prepareStatement("SELECT ID, IMAGE FROM EN_PRODUCT WHERE OCTET_LENGTH(IMAGE) = ?").use { ps ->
            ps.setInt(1, LEGACY_IMAGE_SIZE)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    padded[rs.getLong(1)] = rs.getBytes(2)
                }
            }
        }
        connection.prepareStatement("UPDATE EN_PRODUCT SET IMAGE = ? WHERE ID = ?").use { ps ->
            for ((id, image) in padded) {
                val length = image.indexOfLast { it != 0.toByte() } + 1
                if (length == image.size) continue
                if (length == 0) ps.setNull(1, java.sql.Types.VARBINARY) else ps.setBytes(1, image.copyOf(length))
                ps.setLong(2, id)
                ps.executeUpdate()
            }
        }
    }

    @Throws(SQLException::class)
    fun step03_createOrderingIndexes() {
        connection.createStatement().use { stmt ->
            for (index in ORDERING_INDEXES) {
                stmt.execute("CREATE INDEX IF NOT EXISTS $index")
            }
        }
    }

    private companion object {
        const val LEGACY_IMAGE_SIZE = 1_000_000

        val ORDERING_INDEXES = listOf(
            "IX_USER_NAME ON EN_USER (NAME)",
            "IX_USER_USERNAME ON EN_USER (USERNAME)",
            "IX_PRODUCT_NAME ON EN_PRODUCT (NAME)",
            "IX_PRODUCT_PRICE ON EN_PRODUCT (PRICE)",
            "IX_PURCHASE_BUYDATE ON EN_PURCHASE (BUYDATE)",
            "IX_PURCHASEITEM_PRICE ON EN_PURCHASEITEM (PRICE)",
            "IX_PURCHASEITEM_AMOUNT ON EN_PURCHASEITEM (AMOUNT)",
        )
    }
}
