package br.com.wdc.shopping.scripts.sgbd

import java.math.BigDecimal
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.sql.Connection
import java.sql.SQLException
import java.time.LocalDateTime

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
            // sessões e segredos referenciam o usuário: sem limpá-los antes, a carga falha depois de qualquer login
            "EN_USER_SESSION",
            "EN_USER_INTENT_SECRET",
            "EN_PURCHASEITEM",
            "EN_PURCHASE",
            "EN_PRODUCT",
            "EN_USER",
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
        restartSequence(c, "SQ_USER", id)

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

        restartSequence(c, "SQ_PRODUCT", id)

        // Purchases
        id = 0
        addPurchase(c, id.also { ADMIN_FIRST_PURCHASE_ID = it; id++ }, ADMIN_ID, intArrayOf(2010, 1, 1, 14, 30))
        addPurchase(c, id.also { ADMIN_SECOND_PURCHASE_ID = it; id++ }, ADMIN_ID, intArrayOf(2011, 4, 3, 9, 15))
        restartSequence(c, "SQ_PURCHASE", id)

        // Purchase items
        id = 0
        addPurchaseItem(c, id.also { ADMIN_FIRST_PURCHASE_ITEM0_ID = it; id++ }, ADMIN_FIRST_PURCHASE_ID, CAFETEIRA_ID, 1, 200.0)
        addPurchaseItem(c, id.also { ADMIN_SECOND_PURCHASE_ITEM0_ID = it; id++ }, ADMIN_SECOND_PURCHASE_ID, BOLA_WILSON_ID, 1, 45.30)
        addPurchaseItem(c, id.also { ADMIN_SECOND_PURCHASE_ITEM1_ID = it; id++ }, ADMIN_SECOND_PURCHASE_ID, FITA_VEDA_ROSCA_ID, 1, 2.67)
        restartSequence(c, "SQ_PURCHASEITEM", id)
    }

    private fun restartSequence(c: Connection, name: String, value: Long) {
        c.createStatement().use { stmt -> stmt.execute("ALTER SEQUENCE $name RESTART WITH $value") }
    }

    private fun addUser(c: Connection, id: Long, userName: String, password: String?, name: String, roles: String) {
        c.prepareStatement("INSERT INTO EN_USER (ID, USERNAME, PASSWORD, NAME, ROLES) VALUES (?, ?, ?, ?, ?)").use { ps ->
            ps.setLong(1, id)
            ps.setString(2, userName)
            ps.setString(3, password?.takeIf { it.isNotBlank() }?.let(::passwordDigest))
            ps.setString(4, name)
            ps.setString(5, roles)
            ps.executeUpdate()
        }
    }

    private fun addProduct(c: Connection, id: Long, name: String, price: Double, description: String, image: String?) {
        val imageBytes = image?.let { DBReset::class.java.getResourceAsStream("/META-INF/$it") }?.use { it.readAllBytes() }
        c.prepareStatement("INSERT INTO EN_PRODUCT (ID, NAME, PRICE, DESCRIPTION, IMAGE) VALUES (?, ?, ?, ?, ?)").use { ps ->
            ps.setLong(1, id)
            ps.setString(2, name)
            ps.setBigDecimal(3, BigDecimal.valueOf(price))
            ps.setString(4, description)
            ps.setBytes(5, imageBytes)
            ps.executeUpdate()
        }
    }

    /** [date] é o instante da compra em UTC — a convenção da coluna, que não guarda fuso. */
    private fun addPurchase(c: Connection, id: Long, userId: Long, date: IntArray) {
        val buyDate = LocalDateTime.of(date[0], date[1], date[2], date.getOrElse(3) { 0 }, date.getOrElse(4) { 0 })
        c.prepareStatement("INSERT INTO EN_PURCHASE (ID, USERID, BUYDATE) VALUES (?, ?, ?)").use { ps ->
            ps.setLong(1, id)
            ps.setLong(2, userId)
            ps.setObject(3, buyDate)
            ps.executeUpdate()
        }
    }

    private fun addPurchaseItem(c: Connection, id: Long, purchaseId: Long, productId: Long, amount: Int, price: Double) {
        c.prepareStatement("INSERT INTO EN_PURCHASEITEM (ID, PURCHASEID, PRODUCTID, AMOUNT, PRICE) VALUES (?, ?, ?, ?, ?)").use { ps ->
            ps.setLong(1, id)
            ps.setLong(2, purchaseId)
            ps.setLong(3, productId)
            ps.setInt(4, amount)
            ps.setBigDecimal(5, BigDecimal.valueOf(price))
            ps.executeUpdate()
        }
    }

    /**
     * O resumo da senha como a aplicação o calcula (`PasswordUtil.hashPassword`): MD5 lido como inteiro **sem
     * sinal**, em base 36. Lido com sinal, o resumo sai diferente sempre que o primeiro bit do MD5 é 1 — e o
     * usuário não consegue autenticar.
     */
    fun passwordDigest(password: String): String =
        BigInteger(1, MessageDigest.getInstance("MD5").digest(password.toByteArray(StandardCharsets.UTF_8))).toString(36)
}
