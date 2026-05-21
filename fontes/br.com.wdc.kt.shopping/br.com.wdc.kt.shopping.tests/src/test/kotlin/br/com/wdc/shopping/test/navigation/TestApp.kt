package br.com.wdc.shopping.test.navigation

import br.com.wdc.framework.cube.CubeApplication

class TestApp : CubeApplication() {

    var historyUpdateCount = 0
        private set

    override fun setAttribute(name: String, value: Any?): Any? = null

    override fun getAttribute(name: String): Any? = null

    override fun removeAttribute(name: String): Any? = null

    override fun updateHistory() {
        historyUpdateCount++
    }

    fun presenterCount(): Int {
        var count = 0
        forEachPresenter { count++ }
        return count
    }
}
