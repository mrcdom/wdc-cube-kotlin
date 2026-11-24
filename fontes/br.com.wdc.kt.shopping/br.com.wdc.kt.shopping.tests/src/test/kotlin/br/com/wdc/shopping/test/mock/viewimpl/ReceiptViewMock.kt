package br.com.wdc.shopping.test.mock.viewimpl

import br.com.wdc.framework.cube.CubeView
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.ReceiptPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.ReceiptScope
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.structs.ReceiptItem
import br.com.wdc.shopping.test.mock.RootPresenterMock
import org.junit.jupiter.api.Assertions
import java.util.Date

class ReceiptViewMock(
    app: RootPresenterMock,
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

    var scope: ReceiptScope = presenter.scope

    fun printReceipt() {
        if (scope.notifySuccess) {
            println("Purchase completed successfully")
            println()
        }

        println("Print your receipt:")

        println("------------------------------------------------------------")
        println("WeDoCode Storefront - THE RIGHT PURCHASE ONLINE")
        println("Purchase receipt")
        println("Date: ${Date(scope.receipt!!.date!!)}")
        println("------------------------------------------------------------")
        for (item: ReceiptItem in scope.receipt!!.items) {
            print(item.description)
            print("(${item.quantity})=\$")
            println(item.value)
        }
        println("------------------------------------------------------------")
        println("TOTAL: ${scope.receipt!!.total}")
    }
}
