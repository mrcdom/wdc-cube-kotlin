package br.com.wdc.shopping.test.presentation

import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.commons.serialization.installCommon
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.shopping.presentation.presenter.open.login.LoginScope
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartScope
import br.com.wdc.shopping.presentation.presenter.restricted.cart.structs.CartItem
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomeScope
import br.com.wdc.shopping.presentation.presenter.restricted.home.products.ProductsPanelScope
import br.com.wdc.shopping.presentation.presenter.restricted.home.purchases.PurchasesPanelScope
import br.com.wdc.shopping.presentation.presenter.restricted.home.structs.PurchaseInfo
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.ReceiptScope
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.structs.ReceiptForm
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.structs.ReceiptItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/** The JSON each Storefront scope sends to a remote view, as the React client reads it. */
class ScopeJsonTest {

    init {
        JsonOutputFactory.installCommon()
    }

    private class View(override val instanceId: String) : CubeView {
        override fun update() {}
        override fun release() {}
    }

    @Test
    fun login_neverSendsThePassword() {
        val scope = LoginScope().apply { userName = "admin"; password = "admin"; loading = true }
        val json = scope.toJson("c:1")
        assertEquals("""{"id":"c:1","userName":"admin","errorCode":0,"loading":true}""", json)
        assertFalse("password" in json)
    }

    @Test
    fun home_sendsTheViewsItHolds_asViewIds() {
        val scope = HomeScope().apply {
            contentView = View("p:7"); productsPanelView = View("a:2"); nickName = "John"; cartItemCount = 3
        }
        assertEquals(
            """{"id":"h:1","contentViewId":"p:7","productsPanelViewId":"a:2","nickName":"John","cartItemCount":3,"errorCode":0}""",
            scope.toJson("h:1"),
        )
    }

    @Test
    fun cart_itemsKeepTheirShape() {
        val item = CartItem().apply { id = 4; name = "Ball"; price = 12.5; quantity = 2; image = "x.png" }
        assertEquals(
            """{"id":"c:2","items":[{"id":4,"name":"Ball","price":12.5,"quantity":2}],"errorCode":0}""",
            CartScope().apply { items = listOf(item) }.toJson("c:2"),
        )
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
