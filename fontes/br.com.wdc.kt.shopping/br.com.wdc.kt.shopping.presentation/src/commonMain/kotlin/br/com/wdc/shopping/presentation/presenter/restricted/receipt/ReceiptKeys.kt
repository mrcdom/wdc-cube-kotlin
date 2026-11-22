package br.com.wdc.shopping.presentation.presenter.restricted.receipt

import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.presenter.RootKeys
import br.com.wdc.shopping.presentation.presenter.Routes
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomeKeys

/** The keys of the receipt place. See [RootKeys]. */
class ReceiptKeys(app: ShoppingApplication, intent: CubeIntent? = null) : HomeKeys(app, intent) {

    companion object {
        const val PREFIX = "rc"
    }

    private object P {
        /** The purchase whose receipt is shown. */
        const val PURCHASE_ID = "$PREFIX-id"
    }

    private object A {
        /** Set by the cart right after the purchase: the receipt congratulates the buyer. */
        const val PURCHASE_MADE = "$PREFIX-purchase-made"
    }

    override suspend fun go(): Boolean = Routes.receipt(app, intent)

    /** A positive id, or `null` when the address carries none it can use. */
    var purchaseId: Long?
        get() = intent.getParameterAsLong(P.PURCHASE_ID)?.takeIf { it > 0 }
        set(value) = intent.setParameter(P.PURCHASE_ID, value?.takeIf { it > 0 })

    /** Whether the receipt comes right after the purchase (not from the address). */
    var purchaseMade: Boolean
        get() = intent.getAttribute(A.PURCHASE_MADE) == true
        set(value) = intent.setAttribute(A.PURCHASE_MADE, value.takeIf { it })
}
