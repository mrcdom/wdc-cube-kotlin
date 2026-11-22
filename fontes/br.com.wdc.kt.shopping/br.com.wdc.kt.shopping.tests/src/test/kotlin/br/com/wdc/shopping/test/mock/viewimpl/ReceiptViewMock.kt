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

    fun printReceipt() {
        if (state.notifySuccess) {
            println("Purchase completed successfully")
            println()
        }

        println("Print your receipt:")

        println("------------------------------------------------------------")
        println("WeDoCode Shopping - THE RIGHT PURCHASE ONLINE")
        println("Purchase receipt")
        println("Date: ${Date(state.receipt!!.date!!)}")
        println("------------------------------------------------------------")
        for (item: ReceiptItem in state.receipt!!.items) {
            print(item.description)
            print("(${item.quantity})=\$")
            println(item.value)
        }
        println("------------------------------------------------------------")
        println("TOTAL: ${state.receipt!!.total}")
    }
}
