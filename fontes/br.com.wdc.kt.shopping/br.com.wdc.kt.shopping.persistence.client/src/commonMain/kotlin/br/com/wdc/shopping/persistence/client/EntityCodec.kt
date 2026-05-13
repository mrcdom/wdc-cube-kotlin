package br.com.wdc.shopping.persistence.client

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.SerializationToken
import br.com.wdc.shopping.domain.model.Product
import br.com.wdc.shopping.domain.model.Purchase
import br.com.wdc.shopping.domain.model.PurchaseItem
import br.com.wdc.shopping.domain.model.User
import kotlinx.datetime.Instant

// ── Product ──

internal fun Product.writeTo(out: ExtensibleObjectOutput) {
    out.beginObject()
    id?.let { out.name("id").value(it) }
    name?.let { out.name("name").value(it) }
    price?.let { out.name("price").value(it) }
    description?.let { out.name("description").value(it) }
    // image excluded (handled via separate endpoints)
    out.endObject()
}

internal fun ExtensibleObjectInput.readProduct(): Product = Product().also { p ->
    beginObject()
    while (hasNext()) {
        when (nextName()) {
            "id" -> p.id = nextLong()
            "name" -> p.name = nextString()
            "price" -> p.price = nextDouble()
            "description" -> p.description = nextString()
            else -> skipValue()
        }
    }
    endObject()
}

// ── User ──

internal fun User.writeTo(out: ExtensibleObjectOutput) {
    out.beginObject()
    id?.let { out.name("id").value(it) }
    userName?.let { out.name("userName").value(it) }
    password?.let { out.name("password").value(it) }
    name?.let { out.name("name").value(it) }
    roles?.let { out.name("roles").value(it) }
    out.endObject()
}

internal fun ExtensibleObjectInput.readUser(): User = User().also { u ->
    beginObject()
    while (hasNext()) {
        when (nextName()) {
            "id" -> u.id = nextLong()
            "userName" -> u.userName = nextString()
            "password" -> u.password = nextString()
            "name" -> u.name = nextString()
            "roles" -> u.roles = nextString()
            else -> skipValue()
        }
    }
    endObject()
}

// ── Purchase ──

internal fun Purchase.writeTo(out: ExtensibleObjectOutput) {
    out.beginObject()
    id?.let { out.name("id").value(it) }
    buyDate?.let { out.name("buyDate").value(it.toString()) }
    user?.let { out.name("user"); it.writeTo(out) }
    items?.let { list ->
        out.name("items").beginArray()
        for (item in list) { item.writeTo(out) }
        out.endArray()
    }
    out.endObject()
}

internal fun ExtensibleObjectInput.readPurchase(): Purchase = Purchase().also { p ->
    beginObject()
    while (hasNext()) {
        when (nextName()) {
            "id" -> p.id = nextLong()
            "buyDate" -> p.buyDate = Instant.parse(nextString())
            "user" -> p.user = readUser()
            "items" -> {
                val list = mutableListOf<PurchaseItem>()
                beginArray()
                while (hasNext()) { list.add(readPurchaseItem()) }
                endArray()
                p.items = list
            }
            else -> skipValue()
        }
    }
    endObject()
    // Restaura referência circular: PurchaseItem.purchase → Purchase
    p.items?.forEach { it.purchase = p }
}

// ── PurchaseItem ──

internal fun PurchaseItem.writeTo(out: ExtensibleObjectOutput) {
    out.beginObject()
    id?.let { out.name("id").value(it) }
    amount?.let { out.name("amount").value(it.toLong()) }
    price?.let { out.name("price").value(it) }
    product?.let { out.name("product"); it.writeTo(out) }
    // purchase excluded (circular reference)
    out.endObject()
}

internal fun ExtensibleObjectInput.readPurchaseItem(): PurchaseItem = PurchaseItem().also { pi ->
    beginObject()
    while (hasNext()) {
        when (nextName()) {
            "id" -> pi.id = nextLong()
            "amount" -> pi.amount = nextInt()
            "price" -> pi.price = nextDouble()
            "product" -> pi.product = readProduct()
            else -> skipValue()
        }
    }
    endObject()
}

// ── Utility ──

internal fun bytesToHex(bytes: ByteArray): String {
    val sb = StringBuilder(bytes.size * 2)
    for (b in bytes) {
        val v = b.toInt() and 0xFF
        sb.append(HEX_CHARS[v ushr 4])
        sb.append(HEX_CHARS[v and 0x0F])
    }
    return sb.toString()
}

private val HEX_CHARS = "0123456789abcdef".toCharArray()
