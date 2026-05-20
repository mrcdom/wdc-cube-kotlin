package br.com.wdc.shopping.test

import br.com.wdc.shopping.presentation.presenter.Routes
import br.com.wdc.shopping.scripts.sgbd.DBReset
import br.com.wdc.shopping.test.mock.viewimpl.CartViewMock
import br.com.wdc.shopping.test.mock.viewimpl.LoginViewMock
import br.com.wdc.shopping.test.mock.viewimpl.ProductViewMock
import br.com.wdc.shopping.test.mock.viewimpl.ReceiptViewMock
import br.com.wdc.shopping.test.mock.viewimpl.RestrictedViewMock
import br.com.wdc.shopping.test.util.BasePresentationTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ShoppingWorkflowTest : BasePresentationTest() {

    private fun gotoRestricted(): RestrictedViewMock {
        Routes.login(app)

        val rootView = app.getRootView()!!

        val loginView = LoginViewMock.cast(rootView.state.contentView)
        loginView.state.userName = "admin"
        loginView.state.password = "admin"
        loginView.presenter.onEnter()

        return RestrictedViewMock.cast(rootView.state.contentView)
    }

    @Test
    fun testVisualizaProdutoInexistente() {
        var restrictedView = gotoRestricted()
        val rootView = app.getRootView()!!

        // Produto que não existe
        restrictedView.presenter.onOpenProduct(Long.MIN_VALUE)
        restrictedView = RestrictedViewMock.cast(rootView.state.contentView)
        assertTrue(
            restrictedView.state.errorCode == 3,
            "O código de erro deve estar indicado produto não existe: ${restrictedView.state.errorCode}"
        )
    }

    @Test
    fun testVisualizaProduto() {
        var restrictedView = gotoRestricted()
        val rootView = app.getRootView()!!

        restrictedView.presenter.onOpenProduct(DBReset.PEN_DRIVE2GB_ID)
        restrictedView = RestrictedViewMock.cast(rootView.state.contentView)

        val produtoView = ProductViewMock.cast(restrictedView.state.contentView)
        assertTrue(produtoView.state.product != null, "Produto deve ter sido selecionado")
    }

    @Test
    fun testComprarProduto() {
        Routes.login(app)

        val rootView = app.getRootView()!!

        val loginView = LoginViewMock.cast(rootView.state.contentView)
        loginView.state.userName = "admin"
        loginView.state.password = "admin"
        loginView.presenter.onEnter()

        var homeView = RestrictedViewMock.cast(rootView.state.contentView)

        homeView.presenter.onOpenProduct(DBReset.PEN_DRIVE2GB_ID)
        homeView = RestrictedViewMock.cast(rootView.state.contentView)
        var produtoView = ProductViewMock.cast(homeView.state.contentView)
        assertTrue(produtoView.state.product != null, "Produto deve ter sido selecionado")
        assertTrue(
            produtoView.state.product!!.id == DBReset.PEN_DRIVE2GB_ID,
            "Produto deve ser o id==${DBReset.PEN_DRIVE2GB_ID}"
        )

        produtoView.presenter.onAddToCart(1)
        homeView = RestrictedViewMock.cast(rootView.state.contentView)
        var carrinhoView = CartViewMock.cast(homeView.state.contentView)
        assertTrue(carrinhoView.state.errorCode == 0, "Não deve haver indicação de erros")
        assertTrue(carrinhoView.state.items.size == 1, "Um item no carrinho")
        assertTrue(carrinhoView.state.items[0].quantity == 1, "O item deve ter quantidade 1")
        assertTrue(
            carrinhoView.state.items[0].id == DBReset.PEN_DRIVE2GB_ID,
            "ID no carriho <> ${DBReset.PEN_DRIVE2GB_ID}"
        )

        carrinhoView.presenter.onModifyQuantity(DBReset.PEN_DRIVE2GB_ID, 0)
        homeView = RestrictedViewMock.cast(rootView.state.contentView)
        carrinhoView = CartViewMock.cast(homeView.state.contentView)
        assertTrue(carrinhoView.state.errorCode == 1, "Indicação de quantidade inválida")
        carrinhoView.state.errorCode = 0

        carrinhoView.presenter.onModifyQuantity(DBReset.PEN_DRIVE2GB_ID, 2)
        homeView = RestrictedViewMock.cast(rootView.state.contentView)
        carrinhoView = CartViewMock.cast(homeView.state.contentView)
        assertTrue(carrinhoView.state.errorCode == 0, "Tem que funcionar sem erro")
        assertTrue(carrinhoView.state.items[0].quantity == 2, "O item deve ter quantidade 2")

        carrinhoView.presenter.onModifyQuantity(Long.MIN_VALUE, 2)
        carrinhoView = CartViewMock.cast(homeView.state.contentView)
        assertTrue(carrinhoView.state.errorCode == 2, "Produto não encontrado")
        carrinhoView.state.errorCode = 0

        carrinhoView.presenter.onOpenProducts()
        homeView = RestrictedViewMock.cast(rootView.state.contentView)

        homeView.presenter.onOpenProduct(DBReset.BOLA_WILSON_ID)
        produtoView = ProductViewMock.cast(homeView.state.contentView)
        assertTrue(
            produtoView.state.product!!.id == DBReset.BOLA_WILSON_ID,
            "Produto BOLA_WILSON não localizado"
        )

        produtoView.presenter.onOpenProducts()
        homeView = RestrictedViewMock.cast(rootView.state.contentView)

        homeView.presenter.onOpenProduct(DBReset.FITA_VEDA_ROSCA_ID)
        homeView = RestrictedViewMock.cast(rootView.state.contentView)
        produtoView = ProductViewMock.cast(homeView.state.contentView)
        assertTrue(
            produtoView.state.product!!.id == DBReset.FITA_VEDA_ROSCA_ID,
            "Produto FITA_VEDA_ROSCA não localizado"
        )

        produtoView.presenter.onAddToCart(1)
        homeView = RestrictedViewMock.cast(rootView.state.contentView)
        carrinhoView = CartViewMock.cast(homeView.state.contentView)
        assertTrue(carrinhoView.state.items.size == 2, "Um item no carrinho")
        assertTrue(carrinhoView.state.items[1].quantity == 1, "O item deve ter quantidade 1")

        carrinhoView.presenter.onBuy()
        homeView = RestrictedViewMock.cast(rootView.state.contentView)
        val reciboView = ReceiptViewMock.cast(homeView.state.contentView)
        assertTrue(reciboView.state.notifySuccess, "Tem que estar marcado como novo recibo")
        assertNotNull(reciboView.state.receipt)
        assertEquals(2, reciboView.state.receipt!!.items.size)

        reciboView.presenter.onOpenProducts()
        homeView = RestrictedViewMock.cast(rootView.state.contentView)
        assertNull(
            homeView.state.contentView,
            "A visão restrita deveria estar mostrando o conteúdo padrão"
        )
    }
}
