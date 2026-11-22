package br.com.wdc.framework.cube

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import kotlin.test.Test
import kotlin.test.assertEquals

class ObservableViewStateTest {

    private class TestState : ObservableViewState() {
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
        val state = observed(TestState())

        fun attach(v: CubeView) {
            view = v
        }
    }

    @Test
    fun assigningADifferentValue_notifies_andTheSameValueDoesNot() {
        val state = TestState()
        var changes = 0
        state.observe { changes++ }

        state.name = "a"
        state.count = 1
        assertEquals(2, changes)

        state.name = "a"
        state.count = 1
        state.items = emptyList()
        assertEquals(2, changes, "equal values are not a change")

        state.items = listOf("x")
        state.items = listOf("x")
        assertEquals(3, changes, "lists compare by content")
    }

    @Test
    fun aChangeInsideAnObject_needsChanged() {
        val state = TestState()
        var changes = 0
        state.observe { changes++ }

        state.tags += "inside"
        assertEquals(0, changes, "the field cannot see a mutation inside the object")
        state.changed()
        assertEquals(1, changes)
    }

    @Test
    fun observedState_updatesThePresentersView_withoutAnExplicitUpdate() {
        val presenter = TestPresenter()
        val view = TestView()

        presenter.state.name = "before the view"
        presenter.attach(view)
        presenter.state.name = "with the view"
        presenter.state.count = 7

        assertEquals(2, view.updates)
    }
}
