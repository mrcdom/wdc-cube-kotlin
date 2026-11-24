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

    @JvmField var COFFEE_MAKER_ID: Long = 0
    @JvmField var WILSON_BALL_ID: Long = 0
    @JvmField var THREAD_SEAL_TAPE_ID: Long = 0
    @JvmField var FLASH_DRIVE_2GB_ID: Long = 0

    @JvmField var ADMIN_FIRST_PURCHASE_ID: Long = 0
    @JvmField var ADMIN_FIRST_PURCHASE_ITEM0_ID: Long = 0

    @JvmField var ADMIN_SECOND_PURCHASE_ID: Long = 0
    @JvmField var ADMIN_SECOND_PURCHASE_ITEM0_ID: Long = 0
    @JvmField var ADMIN_SECOND_PURCHASE_ITEM1_ID: Long = 0

    @Throws(SQLException::class)
    fun run(c: Connection) {
        // Clean all
        for (tbName in arrayOf(
            // sessions and secrets reference the user: without clearing them first, the seed fails after any login
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
        id = 1
        addUser(c, id.also { ADMIN_ID = it; id++ }, "admin", "admin", "John Smith", "ADMIN")
        addUser(c, id.also { FULANO_ID = it; id++ }, "fulano", "fulano", "John Doe", "CUSTOMER")
        addUser(c, id.also { BEOTRANO_ID = it; id++ }, "beotrano", "beotrano", "Jane Roe", "CUSTOMER")
        restartSequence(c, "SQ_USER", id)

        // Products
        id = 1
        addProduct(c, id.also { COFFEE_MAKER_ID = it; id++ }, "Italian Design Coffee Maker", 199.99,
            "<ul>" +
                "<li>Capacity for 30 cups (50ml each) or 24 cups (62ml each)</li>" +
                "<li>Drip-stop system</li>" +
                "<li>Comes with a removable permanent filter and a measuring spoon</li>" +
                "<li>Paper filters can also be used</li>" +
                "<li>Graduated water reservoir</li>" +
                "<li>Illuminated on/off switch</li>" +
                "<li>Easy to clean</li>" +
                "<li>Dishwasher-safe parts (except the motor base)</li>" +
                "<li>Power: 1000W - equivalent to 1 kWh (kilowatt-hour).</li>" +
                "</ul>",
            "images/cafeteira.png"
        )

        addProduct(c, id.also { WILSON_BALL_ID = it; id++ }, "Wilson Ball", 45.30,
            "<ul>" +
                "<li>Wilson ball, official size and weight.</li>" +
                "<li>Warranty: against manufacturing defects.</li>" +
                "<li>Origin: imported.</li>" +
                "</ul>",
            "images/wilson.png"
        )

        addProduct(c, id.also { THREAD_SEAL_TAPE_ID = it; id++ }, "Thread Seal Tape", 2.67,
            "<ul>" +
                "<li>Tigre brand.</li>" +
                "<li>Size: 18 mm x 10 m.</li>" +
                "<li>Material: Teflon.</li>" +
                "<li>Use: sealing threaded joints.</li>" +
                "</ul>",
            "images/vedarosca.png"
        )

        addProduct(c, id.also { FLASH_DRIVE_2GB_ID = it; id++ }, "USB Flash Drive 2GB", 16.0,
            "Ideal for carrying data files, audio, video, " +
                "photos and much more. Best value for storing and transferring information. Portable, " +
                "easy to use and super light, it keeps your data safe, has an LED that shows when it is in use, " +
                "and is drop resistant. A flash drive with 2 GB of storage capacity: convenience " +
                "and quality for your files!",
            "images/pendrive2gb.png"
        )

        restartSequence(c, "SQ_PRODUCT", id)

        // Purchases
        id = 1
        addPurchase(c, id.also { ADMIN_FIRST_PURCHASE_ID = it; id++ }, ADMIN_ID, intArrayOf(2010, 1, 1, 14, 30))
        addPurchase(c, id.also { ADMIN_SECOND_PURCHASE_ID = it; id++ }, ADMIN_ID, intArrayOf(2011, 4, 3, 9, 15))
        restartSequence(c, "SQ_PURCHASE", id)

        // Purchase items
        id = 1
        addPurchaseItem(c, id.also { ADMIN_FIRST_PURCHASE_ITEM0_ID = it; id++ }, ADMIN_FIRST_PURCHASE_ID, COFFEE_MAKER_ID, 1, 200.0)
        addPurchaseItem(c, id.also { ADMIN_SECOND_PURCHASE_ITEM0_ID = it; id++ }, ADMIN_SECOND_PURCHASE_ID, WILSON_BALL_ID, 1, 45.30)
        addPurchaseItem(c, id.also { ADMIN_SECOND_PURCHASE_ITEM1_ID = it; id++ }, ADMIN_SECOND_PURCHASE_ID, THREAD_SEAL_TAPE_ID, 1, 2.67)
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

    /** [date] is the purchase instant in UTC — the column's convention, which stores no time zone. */
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
     * The password digest as the application computes it (`PasswordUtil.hashPassword`): MD5 read as an
     * **unsigned** integer, in base 36. Read as signed, the digest comes out different whenever the first bit of
     * the MD5 is 1 — and the user cannot authenticate.
     */
    fun passwordDigest(password: String): String =
        BigInteger(1, MessageDigest.getInstance("MD5").digest(password.toByteArray(StandardCharsets.UTF_8))).toString(36)
}
