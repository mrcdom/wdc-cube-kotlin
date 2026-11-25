package com.example.tasks

import br.com.wdc.framework.cube.CubeIntent
import com.example.tasks.data.CallHttpClient
import com.example.tasks.detail.TaskScope
import com.example.tasks.list.TaskListScope
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * The presenters, driven through their scopes as a view would — no view involved: a presenter only makes scopes. Their
 * calls go to a [FakeBackend] instead of the real one: no server needed.
 */
class TasksFlowTest {

    /** The application without a platform: the address goes to [address] instead of the browser's history. */
    private class TestApp : TasksApp() {
        var address: String? = null

        override fun updateHistory() {
            val intent = CubeIntent()
            intent.place = getLastPlace() ?: RootKeys.PLACE
            publishParameters(intent)
            address = intent.toString()
        }
    }

    private lateinit var backend: FakeBackend
    private lateinit var app: TestApp

    @BeforeTest
    fun setUp() {
        backend = FakeBackend(listOf("Write the README", "Run the tests"))
        CallHttpClient.BEAN.set(backend)
        app = TestApp()
    }

    private val list: TaskListScope get() = assertIs<TaskListScope>(app.scope.content)

    @Test
    fun theRootAlone_goesOnToTheList() = runTest {
        RootKeys(app).flip()
        assertEquals(listOf("Write the README", "Run the tests"), list.rows.map { it.title })
    }

    @Test
    fun addingATask_addsARow_andAnEmptyTitleIsRefused() = runTest {
        RootKeys(app).flip()

        list.onAdd("  ")
        assertEquals(1, list.errorCode)
        assertEquals("Type what there is to do.", list.errorMessage)
        assertEquals(2, list.rows.size)

        list.onAdd("Ship it")
        assertNull(list.errorMessage)
        assertEquals("Ship it", list.rows.last().title)
    }

    @Test
    fun theRemainingCount_isComputedOncePerPaint() = runTest {
        RootKeys(app).flip()
        list.rows.first().onToggle()

        // the view layer runs this once per paint cycle, before it reads the scopes
        app.commitComputedState()
        assertEquals(1, list.remaining)
    }

    @Test
    fun openingATask_showsItsDetail_inTheListsSlot_andPutsItInTheAddress() = runTest {
        RootKeys(app).flip()
        list.rows.last().onOpen()

        val detail = assertIs<TaskScope>(list.detail)
        assertEquals("Run the tests", detail.title)
        assertEquals("task?tk-id=2", app.address)
    }

    @Test
    fun renamingATask_goesBackToTheList_withTheNewTitle() = runTest {
        RootKeys(app).flip()
        list.rows.first().onOpen()
        assertIs<TaskScope>(list.detail).onRename("Write the starter's README")

        assertNull(list.detail)
        assertEquals("Write the starter's README", list.rows.first().title)
    }

    @Test
    fun anAddressNamingNoTask_opensTheList() = runTest {
        app.flip("task?tk-id=99")
        assertNull(list.detail)
        assertEquals(2, list.rows.size)
    }

    @Test
    fun theList_loadsOnItsFirstNavigation_notOnEveryOne() = runTest {
        RootKeys(app).flip()
        backend.insert("Added behind the list's back")

        // opening and closing a detail goes through the list again: it does not load
        list.rows.first().onOpen()
        assertIs<TaskScope>(list.detail).onClose()
        assertEquals(2, list.rows.size)
        assertEquals(listOf("task-list", "task"), backend.calls)
    }

    @Test
    fun removingATask_goesBackToTheList_whichLoadsAgain() = runTest {
        RootKeys(app).flip()
        list.rows.first().onOpen()
        assertIs<TaskScope>(list.detail).onRemove()

        assertNull(list.detail)
        assertEquals(listOf("Run the tests"), list.rows.map { it.title })
    }

    @Test
    fun openingAnotherTask_reusesTheDetail_andLoadsTheNewOne() = runTest {
        RootKeys(app).flip()
        list.rows.first().onOpen()
        val detail = assertIs<TaskScope>(list.detail)

        list.rows.last().onOpen()
        assertEquals("Run the tests", assertIs<TaskScope>(list.detail).title)
        assertEquals(detail, list.detail, "the same presenter, and its scope, show the other task")
    }
}
