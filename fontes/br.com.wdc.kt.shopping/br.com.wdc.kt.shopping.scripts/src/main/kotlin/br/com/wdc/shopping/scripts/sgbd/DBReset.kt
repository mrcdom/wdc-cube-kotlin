package br.com.wdc.shopping.scripts.sgbd

import br.com.wdc.framework.commons.lang.CoerceUtils
import br.com.wdc.shopping.persistence.repository.product.InsertProductRowCmd
import br.com.wdc.shopping.persistence.repository.purchase.InsertRowPurchaseCmd
import br.com.wdc.shopping.persistence.repository.purchaseitem.InsertRowPurchaseItemCmd
import br.com.wdc.shopping.persistence.repository.user.InsertRowUserCmd
import br.com.wdc.shopping.persistence.schema.EnProduct
import br.com.wdc.shopping.persistence.schema.EnPurchase
import br.com.wdc.shopping.persistence.schema.EnPurchaseItem
import br.com.wdc.shopping.persistence.schema.EnUser
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.sql.Connection
import java.sql.SQLException
import java.util.Calendar

object DBReset {

    @JvmField var ADMIN_ID: Long = 0
    @JvmField var FULANO_ID: Long = 0
    @JvmField var BEOTRANO_ID: Long = 0

    @JvmField var CAFETEIRA_ID: Long = 0
    @JvmField var BOLA_WILSON_ID: Long = 0
    @JvmField var FITA_VEDA_ROSCA_ID: Long = 0
    @JvmField var PEN_DRIVE2GB_ID: Long = 0

    @JvmField var ADMIN_FIRST_PURCHASE_ID: Long = 0
    @JvmField var ADMIN_FIRST_PURCHASE_ITEM0_ID: Long = 0

    @JvmField var ADMIN_SECOND_PURCHASE_ID: Long = 0
    @JvmField var ADMIN_SECOND_PURCHASE_ITEM0_ID: Long = 0
    @JvmField var ADMIN_SECOND_PURCHASE_ITEM1_ID: Long = 0

    @Throws(SQLException::class)
    fun run(c: Connection) {
        // Clean all
        for (tbName in arrayOf(
            EnPurchaseItem.INSTANCE.tableName(),
            EnPurchase.INSTANCE.tableName(),
            EnProduct.INSTANCE.tableName(),
            EnUser.INSTANCE.tableName()
        )) {
            c.createStatement().use { stmt ->
                stmt.execute("DELETE FROM $tbName")
            }
        }

        var id: Long

        // Users
        id = 0
        addUser(c, id.also { ADMIN_ID = it; id++ }, "admin", "admin", "João da Silva", "ADMIN")
        addUser(c, id.also { FULANO_ID = it; id++ }, "fulano", "fulano", "Fulano de Tal", "CUSTOMER")
        addUser(c, id.also { BEOTRANO_ID = it; id++ }, "beotrano", "beotrano", "Beotrano de Alguma Coisa", "CUSTOMER")
        EnUser.INSTANCE.alterSeqUser(c, id)

        // Products
        id = 0
        addProduct(c, id.also { CAFETEIRA_ID = it; id++ }, "Cafeteira design italiano", 199.99,
            "<ul>" +
                "<li>Capacidade para 30 cafés (50ml cada) ou 24 cafés (62ml cada)</li>" +
                "<li>Sistema corta-pingos</li>" +
                "<li>Acompanha filtro permanente removível e colher medidora</li>" +
                "<li>Permite uso de filtro de papel</li>" +
                "<li>Reservatório de água com graduação</li>" +
                "<li>Botão luminoso liga/desliga</li>" +
                "<li>Fácil de lavar</li>" +
                "<li>Peças podem ser lavadas em máquina de lavar louça (exceto a base motora)</li>" +
                "<li>Potência: 1000W - correspondente a 1 Kwh (Kilowatts hora).</li>" +
                "</ul>",
            "images/cafeteira.png"
        )

        addProduct(c, id.also { BOLA_WILSON_ID = it; id++ }, "Bola Wilson", 45.30,
            "<ul>" +
                "<li>Bola Wilson Tamanho e Peso Oficial.</li>" +
                "<li>Garantia: Contra defeito de fabricação.</li>" +
                "<li>Origem: Importada.</li>" +
                "</ul>",
            "images/wilson.png"
        )

        addProduct(c, id.also { FITA_VEDA_ROSCA_ID = it; id++ }, "Fita veda rosca", 2.67,
            "<ul>" +
                "<li>Marca Tigre.</li>" +
                "<li>Tamanho e medida: 18 mm x 10 m.</li>" +
                "<li>Composição: Teflon.</li>" +
                "<li>Utilização: vedação de juntas roscaveis.</li>" +
                "</ul>",
            "images/vedarosca.png"
        )

        addProduct(c, id.also { PEN_DRIVE2GB_ID = it; id++ }, "Pen Drive 2GB", 16.0,
            "Ideal para transporte de arquivos de dados, áudio, vídeo, " +
                "fotos e muito mais. Melhor valor para armazenamento e transferência de informação. Portátil, " +
                "fácil de usar e super leve, ele possui segurança com seus dados, led indicando o uso, além " +
                "de ser resistente a quedas. Pen Drive com capacidade de armazenamento de 2 GB, praticidade " +
                "e qualidade com seus arquivos!",
            "images/pendrive2gb.png"
        )

        EnProduct.INSTANCE.alterSeqProduct(c, id)

        // Purchases
        id = 0
        addPurchase(c, id.also { ADMIN_FIRST_PURCHASE_ID = it; id++ }, ADMIN_ID, intArrayOf(2010, 1, 1, 14, 30))
        addPurchase(c, id.also { ADMIN_SECOND_PURCHASE_ID = it; id++ }, ADMIN_ID, intArrayOf(2011, 4, 3, 9, 15))
        EnPurchase.INSTANCE.alterSeqPurchase(c, id)

        // Purchase items
        id = 0
        addPurchaseItem(c, id.also { ADMIN_FIRST_PURCHASE_ITEM0_ID = it; id++ }, ADMIN_FIRST_PURCHASE_ID, CAFETEIRA_ID, 1, 200.0)
        addPurchaseItem(c, id.also { ADMIN_SECOND_PURCHASE_ITEM0_ID = it; id++ }, ADMIN_SECOND_PURCHASE_ID, BOLA_WILSON_ID, 1, 45.30)
        addPurchaseItem(c, id.also { ADMIN_SECOND_PURCHASE_ITEM1_ID = it; id++ }, ADMIN_SECOND_PURCHASE_ID, FITA_VEDA_ROSCA_ID, 1, 2.67)
        EnPurchaseItem.INSTANCE.alterSeqPurchaseItem(c, id)
    }

    private fun addUser(c: Connection, id: Long, userName: String, password: String?, name: String, roles: String) {
        val row = EnUser.Row()
        row.id(id)
        row.userName(userName)
        if (!password.isNullOrBlank()) {
            val pwd = BigInteger(md5().digest(password.toByteArray(StandardCharsets.UTF_8))).toString(36)
            row.password(pwd)
        }
        row.name(name)
        row.roles(roles)
        InsertRowUserCmd().execute(c, row)
    }

    private fun addProduct(c: Connection, id: Long, name: String, price: Double, description: String, image: String?) {
        val row = EnProduct.Row()
        row.id(id)
        row.name(name)
        row.description(description)
        row.price(BigDecimal.valueOf(price))

        if (image != null) {
            val imageStream = DBReset::class.java.getResourceAsStream("/META-INF/$image")
            if (imageStream != null) {
                imageStream.use { row.image(it.readAllBytes()) }
            }
        }
        InsertProductRowCmd().execute(c, row)
    }

    private fun addPurchase(c: Connection, id: Long, userId: Long, date: IntArray?) {
        val row = EnPurchase.Row()
        row.id(id)
        row.userId(userId)
        if (date != null && date.size >= 3) {
            val cal = Calendar.getInstance()
            cal.set(Calendar.YEAR, date[0])
            cal.set(Calendar.MONTH, date[1] - 1)
            cal.set(Calendar.DAY_OF_MONTH, date[2])
            if (date.size >= 5) {
                cal.set(Calendar.HOUR_OF_DAY, date[3])
                cal.set(Calendar.MINUTE, date[4])
            } else {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
            }
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            row.buyDate(CoerceUtils.asOffsetDateTime(cal.time)!!)
        }
        InsertRowPurchaseCmd().execute(c, row)
    }

    private fun addPurchaseItem(c: Connection, id: Long, purchaseId: Long, productId: Long, amount: Int, price: Double) {
        val row = EnPurchaseItem.Row()
        row.id(id)
        row.purchaseId(purchaseId)
        row.productId(productId)
        row.amount(amount)
        row.price(BigDecimal.valueOf(price))
        InsertRowPurchaseItemCmd().execute(c, row)
    }

    private fun md5(): MessageDigest = MessageDigest.getInstance("MD5")
}
