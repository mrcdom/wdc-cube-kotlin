package br.com.wdc.store.test.mock.viewimpl

import br.com.wdc.store.presentation.presenter.restricted.cart.CartItemScope
import br.com.wdc.store.test.mock.RootPresenterMock

/** A row of the cart. */
class CartItemViewMock(app: RootPresenterMock, scope: CartItemScope) : AbstractViewMock<CartItemScope>(app, scope)
