package br.com.wdc.shopping.presentation.presenter.restricted.home.products

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.log.getLogger
import br.com.wdc.framework.cube.AbstractChildPresenter
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomePresenter
import br.com.wdc.shopping.presentation.presenter.restricted.products.ProductService

class ProductsPanelPresenter(
    app: ShoppingApplication,
    val owner: HomePresenter,
) : AbstractChildPresenter<ShoppingApplication>(app) {

    companion object {
        private val LOG = Log.getLogger(ProductsPanelPresenter::class.java)

        @JvmField
        var createView: ((ProductsPanelPresenter) -> CubeView)? = null
    }

    // :: Public Instance Fields

    val state = ProductsPanelViewState()

    // :: Internal Instance Fields

    private val productService = ProductService(app)

    // :: Life cycle

    override fun onCreateView(): CubeView = createView!!.invoke(this)

    override fun onInitialize() {
        loadProducts()
    }

    // :: User Actions

    fun onOpenProduct(productId: Long?) {
        owner.onOpenProduct(productId)
    }

    // :: Data load

    fun loadProducts() {
        try {
            state.products = productService.loadProductsWithoutDescription(1000)
            update()
        } catch (caught: Exception) {
            LOG.error("Failed to load products", caught)
        }
    }
}
