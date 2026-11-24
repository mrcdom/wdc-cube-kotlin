package br.com.wdc.store.test.presentation

import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.commons.serialization.installCommon
import br.com.wdc.framework.cube.Scope
import br.com.wdc.store.presentation.presenter.open.login.LoginScope
import br.com.wdc.store.presentation.presenter.restricted.cart.CartScope
import br.com.wdc.store.presentation.presenter.restricted.cart.CartItemScope
import br.com.wdc.store.presentation.presenter.restricted.home.HomeScope
import br.com.wdc.store.presentation.presenter.restricted.home.ProductsPanelScope
import br.com.wdc.store.presentation.presenter.restricted.home.PurchasesPanelScope
import br.com.wdc.store.presentation.presenter.restricted.home.structs.PurchaseInfo
import br.com.wdc.store.presentation.presenter.restricted.receipt.ReceiptScope
import br.com.wdc.store.presentation.presenter.restricted.receipt.structs.ReceiptForm
import br.com.wdc.store.presentation.presenter.restricted.receipt.structs.ReceiptItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/** The JSON each WDC Store scope sends to a remote view, as the React client reads it. */
class ScopeJsonTest {

    init {
        JsonOutputFactory.installCommon()
    }

    @Test
    fun login_neverSendsThePassword() {
        val scope = LoginScope().apply { userName = "admin"; password = "admin"; loading = true }
        val json = scope.toJson("c:1")
        assertEquals("""{"id":"c:1","userName":"admin","errorCode":0,"loading":true}""", json)
        assertFalse("password" in json)
    }

    @Test
    fun home_sendsTheScopesItHolds_asTheIdsOfTheirViews() {
        val product = ProductsPanelScope()
        val cart = CartScope()
        val views = mapOf<Scope, String>(cart to "p:7", product to "a:2")
        val scope = HomeScope().apply { content = cart; productsPanel = product; nickName = "John"; cartItemCount = 3 }
        assertEquals(
            """{"id":"h:1","contentViewId":"p:7","productsPanelViewId":"a:2","nickName":"John","cartItemCount":3,"errorCode":0}""",
            scope.toJson("h:1") { views.getValue(it) },
        )
    }

    @Test
    fun cart_sendsItsRows_asTheIdsOfTheirViews_andTheTotal() {
        val row = CartItemScope().apply { productId = 4; name = "Ball"; price = 12.5; quantity = 2; image = "x.png" }
        val views = mapOf<Scope, String>(row to "i:9")
        assertEquals(
            """{"id":"c:2","rowsViewIds":["i:9"],"total":25.0,"errorCode":0}""",
            CartScope().apply { rows = listOf(row); total = 25.0 }.toJson("c:2") { views.getValue(it) },
        )
        assertEquals(
            """{"id":"i:9","productId":4,"name":"Ball","image":"x.png","price":12.5,"quantity":2}""",
            row.toJson("i:9"),
        )
        assertEquals("""{"id":"c:2","rowsViewIds":[],"total":0.0,"errorCode":0}""", CartScope().toJson("c:2") { error("none") })
    }

    @Test
    fun productsPanel_notLoadedYet_sendsNoList() {
        // the React panel shows "Loading products..." for a missing list
        assertEquals("""{"id":"a:2"}""", ProductsPanelScope().toJson("a:2"))
    }

    @Test
    fun purchasesPanel_andReceipt_keepTheirShape() {
        val purchase = PurchaseInfo().apply { id = 9; date = 1000; total = 7.5; items = listOf("Ball") }
        assertEquals(
            """{"id":"b:3","purchases":[{"id":9,"date":1000,"total":7.5,"items":["Ball"]}],"page":1,"pageSize":5,"totalCount":6}""",
            PurchasesPanelScope().apply { purchases = listOf(purchase); page = 1; pageSize = 5; totalCount = 6 }.toJson("b:3"),
        )
        val receipt = ReceiptForm().apply {
            date = 1000; total = 7.5
            items = mutableListOf(ReceiptItem().apply { description = "Ball"; value = 7.5; quantity = 1 })
        }
        assertEquals(
            """{"id":"r:4","notifySuccess":true,"receipt":{"date":1000,"total":7.5,"items":[{"description":"Ball","value":7.5,"quantity":1}]}}""",
            ReceiptScope().apply { notifySuccess = true; this.receipt = receipt }.toJson("r:4"),
        )
    }
}
