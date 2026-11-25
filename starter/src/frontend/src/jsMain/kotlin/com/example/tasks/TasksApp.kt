package com.example.tasks

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.CubeApplication
import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubeTreePlace
import br.com.wdc.framework.cube.PresenterBase
import br.com.wdc.framework.cube.Scope
import br.com.wdc.framework.cube.ScopeSlot
import com.example.tasks.detail.TaskKeys
import com.example.tasks.list.TaskListKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * The application, and the root presenter: it holds the presenters alive and the navigation ([CubeApplication]), and
 * owns the page frame ([scope]): the slot where the first level shows its scope, and the alert of unexpected errors.
 * Each platform extends it with what it needs (the browser's history, in `web/Main.kt`).
 */
open class TasksApp : CubeApplication(PLACES), PresenterBase {

    companion object {
        private val LOG = Log.getLogger("TasksApp")

        /** Every place an address can name. The root (the application itself) is not in it. */
        val PLACES: List<CubeTreePlace> = listOf(TaskListKeys.PLACE, TaskKeys.PLACE)
    }

    // :: Public Instance Fields

    /** Where the views ask the presenters to do things: one coroutine at a time, off the UI's own code. */
    val presenterScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1))

    /** What the root view draws. */
    val scope = RootScope()

    override val app: CubeApplication get() = this

    // :: Internal Instance Fields

    private val contentSlot = ScopeSlot { setContent(it) }

    // :: Cube API

    /**
     * The root's part in every navigation: alone, it shows nothing, so it goes on to the list, with the intent in
     * transit; otherwise it offers the level below its slot.
     */
    override suspend fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean {
        if (deepest) {
            TaskListKeys(this, intent).flip()
            return false
        }
        RootKeys(this, intent).parentSlot = contentSlot
        return true
    }

    /** Both the application (every presenter's) and the root presenter (none of its own) have one. */
    override fun commitComputedState() {
        super<CubeApplication>.commitComputedState()
    }

    /** A failure no action handled: the user sees what failed, the log gets the rest. */
    override fun alertUnexpectedError(logger: Log, message: String, e: Throwable) {
        scope.errorMessage = if (e.message.isNullOrBlank()) message else "$message: ${e.message}"
        logger.error(scope.errorMessage!!, e)
    }

    // :: Slots

    private fun setContent(content: Scope?) {
        if (scope.content !== content) {
            scope.content = content
        }
    }
}

/** The page frame: the scope of the first level, and an error to show. */
class RootScope : Scope() {
    var content: Scope? by field(null)
    var errorMessage: String? by field(null)
}
