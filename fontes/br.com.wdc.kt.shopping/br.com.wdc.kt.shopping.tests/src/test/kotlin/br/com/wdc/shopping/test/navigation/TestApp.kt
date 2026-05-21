package br.com.wdc.shopping.test.navigation

import br.com.wdc.framework.cube.CubeApplication

class TestApp : CubeApplication() {

    var historyUpdateCount = 0
        private set

    override fun updateHistory() {
        historyUpdateCount++
    }

    fun presenterCount(): Int {
        var count = 0
        forEachPresenter { count++ }
        return count
    }
}
