package br.com.wdc.shopping.api.client

import br.com.wdc.shopping.domain.model.Product
import br.com.wdc.shopping.domain.model.Purchase
import br.com.wdc.shopping.domain.model.PurchaseItem
import br.com.wdc.shopping.domain.model.User
import kotlinx.datetime.Instant

// ── Product ──

internal fun Product.toMap(): Map<String, Any?> = buildMap {
    id?.let { put("id", it) }
    name?.let { put("name", it) }
    price?.let { put("price", it) }
    description?.let { put("description", it) }
    // image excluded (handled via separate endpoints)
}

internal fun Map<String, Any?>.toProduct(): Product = Product().apply {
    id = this@toProduct.longOrNull("id")
    name = this@toProduct.stringOrNull("name")
    price = this@toProduct.doubleOrNull("price")
    description = this@toProduct.stringOrNull("description")
}

// ── User ──

internal fun User.toMap(): Map<String, Any?> = buildMap {
    id?.let { put("id", it) }
    userName?.let { put("userName", it) }
    password?.let { put("password", it) }
    name?.let { put("name", it) }
    roles?.let { put("roles", it) }
}

internal fun Map<String, Any?>.toUser(): User = User().apply {
    id = this@toUser.longOrNull("id")
    userName = this@toUser.stringOrNull("userName")
    password = this@toUser.stringOrNull("password")
    name = this@toUser.stringOrNull("name")
    roles = this@toUser.stringOrNull("roles")
}

// ── Purchase ──

internal fun Purchase.toMap(): Map<String, Any?> = buildMap {
    id?.let { put("id", it) }
    buyDate?.let { put("buyDate", it.toString()) }
    user?.let { put("user", it.toMap()) }
    items?.let { list -> put("items", list.map { it.toMap() }) }
}

internal fun Map<String, Any?>.toPurchase(): Purchase = Purchase().apply {
    id = this@toPurchase.longOrNull("id")
    buyDate = this@toPurchase.stringOrNull("buyDate")?.let { Instant.parse(it) }
    user = this@toPurchase.mapOrNull("user")?.toUser()
    items = this@toPurchase.listOrNull("items")?.map { it.toPurchaseItem() }?.toMutableList()
}

// ── PurchaseItem ──

internal fun PurchaseItem.toMap(): Map<String, Any?> = buildMap {
    id?.let { put("id", it) }
    amount?.let { put("amount", it) }
    price?.let { put("price", it) }
    product?.let { put("product", it.toMap()) }
    // purchase excluded (circular reference)
}

internal fun Map<String, Any?>.toPurchaseItem(): PurchaseItem = PurchaseItem().apply {
    id = this@toPurchaseItem.longOrNull("id")
    amount = (this@toPurchaseItem["amount"] as? Number)?.toInt()
    price = this@toPurchaseItem.doubleOrNull("price")
    product = this@toPurchaseItem.mapOrNull("product")?.toProduct()
    // purchase not decoded (circular)
}
