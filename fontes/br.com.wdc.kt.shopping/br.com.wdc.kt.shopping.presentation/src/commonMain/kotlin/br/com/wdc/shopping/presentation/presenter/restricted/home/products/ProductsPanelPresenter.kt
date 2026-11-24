package br.com.wdc.shopping.presentation.presenter.restricted.home.products

import br.com.wdc.shopping.domain.product.ProductRepository
import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.AbstractChildPresenter
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.shopping.presentation.presenter.RootPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomePresenter
import br.com.wdc.shopping.presentation.presenter.restricted.products.ProductService
import br.com.wdc.framework.cube.FormParam

class ProductsPanelPresenter(
    app: RootPresenter,
    val owner: HomePresenter,
) : AbstractChildPresenter<RootPresenter>(app) {

    companion object {
        private val LOG = Log.getLogger("ProductsPanelPresenter")

        var createView: ((ProductsPanelPresenter) -> CubeView)? = null
    }

    // :: Public Instance Fields

    val scope = bind(ProductsPanelScope())

    // :: Internal Instance Fields

    private val productService = ProductService(ProductRepository.BEAN.get())

    init {
        scope.onOpenProduct = actionOf(1, FormParam.long("p.productId")) { owner.openProduct(it) }
    }

    // :: Life cycle

    override fun onCreateView(): CubeView = createView!!.invoke(this)

    override suspend fun onInitialize() {
        loadProducts()
    }

    // :: Data load

    suspend fun loadProducts() {
        try {
            scope.products = productService.loadProductsWithoutDescription(1000)
        } catch (caught: Exception) {
            LOG.error("Failed to load products", caught)
        }
    }
}
