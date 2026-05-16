package br.com.wdc.shopping.test.navigation

import br.com.wdc.framework.cube.CubeApplication
import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubePlace
import br.com.wdc.framework.cube.CubePresenter

/**
 * Configurable test presenter that tracks lifecycle calls and can trigger
 * redirects or throw exceptions during applyParameters.
 */
class TestPresenter(val app: TestApp, val placeId: Int) : CubePresenter {

    var initialized = false
    var released = false
    var applyCount = 0
    var publishCount = 0
    var lastIntent: CubeIntent? = null
    var lastInitialization = false
    var lastDeepest = false

    /** Set to false to make applyParameters return false */
    var goAhead: Boolean = true

    /** Set to trigger a redirect during applyParameters */
    var redirectTo: List<CubePlace>? = null

    /** Set to throw during applyParameters */
    var throwOnApply: Exception? = null

    override fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean {
        applyCount++
        lastIntent = intent
        lastInitialization = initialization
        lastDeepest = deepest

        if (initialization) {
            initialized = true
        }

        throwOnApply?.let { throw it }

        redirectTo?.let { places ->
            val nav = app.navigate<TestApp>()
            val newIntent = CubeIntent()
            for (place in places) {
                nav.step(place)
            }
            nav.execute(newIntent)
            return true
        }

        return goAhead
    }

    override fun publishParameters(intent: CubeIntent) {
        publishCount++
    }

    override fun release() {
        released = true
    }
}
