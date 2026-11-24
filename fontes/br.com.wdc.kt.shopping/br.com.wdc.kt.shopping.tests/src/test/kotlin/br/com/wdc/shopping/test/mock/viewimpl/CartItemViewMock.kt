package br.com.wdc.shopping.test.mock.viewimpl

import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartItemScope
import br.com.wdc.shopping.test.mock.RootPresenterMock

/** A row of the cart. */
class CartItemViewMock(app: RootPresenterMock, scope: CartItemScope) : AbstractViewMock<CartItemScope>(app, scope)
