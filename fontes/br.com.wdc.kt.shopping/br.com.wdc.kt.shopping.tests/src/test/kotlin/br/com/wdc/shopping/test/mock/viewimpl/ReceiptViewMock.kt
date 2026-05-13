package br.com.wdc.shopping.test.mock.viewimpl

import br.com.wdc.framework.cube.CubeView
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.ReceiptPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.ReceiptViewState
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.structs.ReceiptItem
import br.com.wdc.shopping.test.mock.ShoppingApplicationMock
import org.junit.jupiter.api.Assertions
import java.util.Date

class ReceiptViewMock(
    app: ShoppingApplicationMock,
    presenter: ReceiptPresenter,
) : AbstractViewMock<ReceiptPresenter>(app, presenter) {

    companion object {
        fun cast(view: CubeView?): ReceiptViewMock {
            Assertions.assertNotNull(view, "Expecting ReceiptViewMock but this view was null")
            Assertions.assertInstanceOf(ReceiptViewMock::class.java, view,
                "Expecting ReceiptViewMock but it was ${view!!::class.simpleName}")
            return view as ReceiptViewMock
        }
    }

    var state: ReceiptViewState = presenter.state

    fun printRecibo() {
        if (state.notifySuccess) {
            println("Compra efetuada com sucesso")
            println()
        }

        println("Imprima seu recibo:")

        println("------------------------------------------------------------")
        println("WeDoCode Shopping - SUA COMPRA CERTA NA INTERNET")
        println("Recibo de compra")
        println("Data: ${Date(state.receipt!!.date!!)}")
        println("------------------------------------------------------------")
        for (item: ReceiptItem in state.receipt!!.items) {
            print(item.description)
            print("(${item.quantity})=R$ ")
            println(item.value)
        }
        println("------------------------------------------------------------")
        println("TOTAL: ${state.receipt!!.total}")
    }
}
