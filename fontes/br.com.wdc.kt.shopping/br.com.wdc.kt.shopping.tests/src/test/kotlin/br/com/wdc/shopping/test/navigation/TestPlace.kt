package br.com.wdc.shopping.test.navigation

import br.com.wdc.framework.cube.CubeApplication
import br.com.wdc.framework.cube.CubePlace
import br.com.wdc.framework.cube.CubePresenter

/**
 * Test places with sequential IDs. Each place creates a TestPresenter via factory.
 */
enum class TestPlace(override val id: Int) : CubePlace {
    ROOT(1),
    HOME(2),
    PRODUCTS(3),
    PRODUCT_DETAIL(4),
    CART(5),
    RECEIPT(6),
    SETTINGS(7);

    override val placeName: String get() = name

    @Suppress("UNCHECKED_CAST")
    override fun <A : CubeApplication> presenterFactory(): (A) -> CubePresenter = { app ->
        TestPresenter(app as TestApp, id)
    }
}
