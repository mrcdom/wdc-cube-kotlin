package br.com.wdc.shopping.test

import br.com.wdc.shopping.presentation.presenter.Routes
import br.com.wdc.shopping.scripts.sgbd.DBReset
import br.com.wdc.shopping.test.mock.viewimpl.LoginViewMock
import br.com.wdc.shopping.test.mock.viewimpl.PurchasesPanelViewMock
import br.com.wdc.shopping.test.mock.viewimpl.RestrictedViewMock
import br.com.wdc.shopping.test.util.BasePresentationTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ShoppingLoginTest : BasePresentationTest() {

    @Test
    fun testLoginPrimeiroAcesso() {
        Routes.login(app)

        val rootView = app.getRootView()!!

        val mainContent = LoginViewMock.cast(rootView.state.contentView)
        assertTrue(mainContent.state.errorCode == 0, "Usuário não poderia ter sido validado")
    }

    @Test
    fun testLoginFalhaPorSenhaOuUsuarioNaoReconhecidos() {
        Routes.login(app)

        val rootView = app.getRootView()!!

        var loginView = LoginViewMock.cast(rootView.state.contentView)
        loginView.state.userName = "admin"
        loginView.state.password = "senha não reconhecida"
        loginView.presenter.onEnter()

        // Check if it keeps bean login view
        loginView = LoginViewMock.cast(rootView.state.contentView)
        assertTrue(loginView.state.errorCode == 1, "Usuário não poderia ter sido validado")
    }

    @Test
    fun testLoginAcessoAoSistema() {
        Routes.login(app)

        val rootView = app.getRootView()!!

        val loginView = LoginViewMock.cast(rootView.state.contentView)
        loginView.state.userName = "admin"
        loginView.state.password = "admin"
        loginView.presenter.onEnter()

        val restrictedView = RestrictedViewMock.cast(rootView.state.contentView)

        assertTrue(!restrictedView.state.nickName.isNullOrBlank(), "Nome do usuário inválido")
        assertTrue(restrictedView.state.cartItemCount >= 0, "Quantidade itens no carrinho não pode ser negativo")
        assertTrue(restrictedView.state.errorCode == 0, "Usuário deveria ter sido validado")

        val purchasesState = restrictedView.getPurchasesPanelState()
        val productsState = restrictedView.getProductsPanelState()

        assertNotNull(purchasesState.purchases, "Falta lista de compras")
        assertNotNull(productsState.products, "Falta lista de produtos")

        // Simulate the view reporting capacity (no real view in tests)
        val purchasesMock = PurchasesPanelViewMock.cast(restrictedView.state.purchasesPanelView)
        purchasesMock.presenter.onItemSizeCapacityChanged(3)

        assertEquals("João da Silva", restrictedView.state.nickName)
        assertEquals(0, restrictedView.state.cartItemCount)

        // Pagination metadata
        assertEquals(0, purchasesState.page)
        assertEquals(3, purchasesState.pageSize)
        assertEquals(2, purchasesState.totalCount)

        assertEquals(2, purchasesState.purchases.size)

        assertEquals(DBReset.ADMIN_SECOND_PURCHASE_ID, purchasesState.purchases[0].id)
        assertEquals(47.97, purchasesState.purchases[0].total, 0.001)
        assertEquals(2, purchasesState.purchases[0].items.size)
        assertEquals("Bola Wilson", purchasesState.purchases[0].items[0])
        assertEquals("Fita veda rosca", purchasesState.purchases[0].items[1])

        assertEquals(DBReset.ADMIN_FIRST_PURCHASE_ID, purchasesState.purchases[1].id)
        assertEquals(200.0, purchasesState.purchases[1].total, 0.001)
        assertEquals(1, purchasesState.purchases[1].items.size)
        assertEquals("Cafeteira design italiano", purchasesState.purchases[1].items[0])

        assertEquals(4, productsState.products!!.size)

        assertEquals(DBReset.CAFETEIRA_ID, productsState.products!![0].id)
        assertEquals("unknown", productsState.products!![0].description)

        assertEquals(DBReset.BOLA_WILSON_ID, productsState.products!![1].id)
        assertEquals("unknown", productsState.products!![1].description)

        assertEquals(DBReset.FITA_VEDA_ROSCA_ID, productsState.products!![2].id)
        assertEquals("unknown", productsState.products!![2].description)

        assertEquals(DBReset.PEN_DRIVE2GB_ID, productsState.products!![3].id)
        assertEquals("unknown", productsState.products!![3].description)
    }
}
