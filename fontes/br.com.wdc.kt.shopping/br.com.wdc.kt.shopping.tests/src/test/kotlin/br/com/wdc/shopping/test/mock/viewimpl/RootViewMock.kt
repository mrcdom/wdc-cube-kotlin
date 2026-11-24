package br.com.wdc.shopping.test.mock.viewimpl

import br.com.wdc.framework.cube.CubeView
import br.com.wdc.shopping.presentation.presenter.RootScope
import br.com.wdc.shopping.test.mock.RootPresenterMock
import org.junit.jupiter.api.Assertions

class RootViewMock(
    app: RootPresenterMock,
    scope: RootScope,
) : AbstractViewMock<RootScope>(app, scope) {

    companion object {
        fun cast(view: CubeView?): RootViewMock {
            Assertions.assertNotNull(view, "Expecting RootViewMock but this view was null")
            Assertions.assertInstanceOf(RootViewMock::class.java, view,
                "Expecting RootViewMock but it was ${view!!::class.simpleName}")
            return view as RootViewMock
        }
    }

    /** The view of the scope below (asked to the application, as a real view does). */
    val contentView: CubeView? get() = scope.content?.let(app::viewOf)
}
