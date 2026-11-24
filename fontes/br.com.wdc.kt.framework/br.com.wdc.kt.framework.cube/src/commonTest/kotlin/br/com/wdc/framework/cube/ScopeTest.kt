package br.com.wdc.framework.cube

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import kotlin.test.Test
import kotlin.test.assertEquals

class ScopeTest {

    private class TestScope : Scope() {
        var name: String? by field(null)
        var count: Int by field(0)
        var items: List<String> by field(emptyList())
        val tags = mutableListOf<String>()

        override fun write(instanceId: String, json: ExtensibleObjectOutput) {}
    }

    private class TestView : CubeView {
        var updates = 0
        override val instanceId: String = "test"
        override fun update() {
            updates++
        }
        override fun release() {}
    }

    private class TestPresenter : AbstractCubePresenter<CubeApplication>(object : CubeApplication() {}) {
        val scope = bind(TestScope())

        fun attach(v: CubeView) {
            view = v
        }
    }

    @Test
    fun assigningADifferentValue_notifies_andTheSameValueDoesNot() {
        val scope = TestScope()
        var changes = 0
        scope.observe { changes++ }

        scope.name = "a"
        scope.count = 1
        assertEquals(2, changes)

        scope.name = "a"
        scope.count = 1
        scope.items = emptyList()
        assertEquals(2, changes, "equal values are not a change")

        scope.items = listOf("x")
        scope.items = listOf("x")
        assertEquals(3, changes, "lists compare by content")
    }

    @Test
    fun aChangeInsideAnObject_needsChanged() {
        val scope = TestScope()
        var changes = 0
        scope.observe { changes++ }

        scope.tags += "inside"
        assertEquals(0, changes, "the field cannot see a mutation inside the object")
        scope.changed()
        assertEquals(1, changes)
    }

    @Test
    fun observedState_updatesThePresentersView_withoutAnExplicitUpdate() {
        val presenter = TestPresenter()
        val view = TestView()

        presenter.scope.name = "before the view"
        presenter.attach(view)
        presenter.scope.name = "with the view"
        presenter.scope.count = 7

        assertEquals(2, view.updates)
    }
}
