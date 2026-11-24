package br.com.wdc.store.presentation.presenter.restricted.receipt

import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubeTreePlace
import br.com.wdc.store.presentation.presenter.RootPresenter
import br.com.wdc.store.presentation.presenter.RootKeys
import br.com.wdc.store.presentation.presenter.restricted.home.HomeKeys

/** The keys of the receipt place. See [RootKeys]. */
class ReceiptKeys(app: RootPresenter, intent: CubeIntent? = null) : HomeKeys(app, intent, PLACE) {

    companion object {
        const val PREFIX = "rc"

        val PLACE: CubeTreePlace = HomeKeys.PLACE.child("receipt", ::ReceiptPresenter)
    }

    private object P {
        /** The purchase whose receipt is shown. */
        const val PURCHASE_ID = "$PREFIX-id"
    }

    private object A {
        /** Set by the cart right after the purchase: the receipt congratulates the buyer. */
        const val PURCHASE_MADE = "$PREFIX-purchase-made"
    }

    /** A positive id, or `null` when the address carries none it can use. */
    var purchaseId: Long?
        get() = intent.getParameterAsLong(P.PURCHASE_ID)?.takeIf { it > 0 }
        set(value) = intent.setParameter(P.PURCHASE_ID, value?.takeIf { it > 0 })

    /** Whether the receipt comes right after the purchase (not from the address). */
    var purchaseMade: Boolean
        get() = intent.getAttribute(A.PURCHASE_MADE) == true
        set(value) = intent.setAttribute(A.PURCHASE_MADE, value.takeIf { it })
}
