package br.com.wdc.framework.cube

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

/**
 * A place in a tree: it knows its parent, so the [path] from the root to it is the navigation that reaches it —
 * [CubeApplication.flip] steps through it.
 *
 * The tree hangs from [ROOT], which stands for the application itself: it has no presenter and is not a step of any
 * path — the application takes part in every navigation before the first step ([CubeApplication.applyParameters]).
 *
 * Applications declare each place in the keys class of that place ([CubeKeys]), built from the place of the keys it
 * extends, so the tree of keys classes is the tree of places:
 *
 * ```kotlin
 * companion object {
 *     val PLACE = HomeKeys.PLACE.child("product") { app: MyApplication -> ProductPresenter(app) }
 * }
 * ```
 *
 * **Ids grow in creation order**, and a place is created after its parent, so a child always has a larger id than
 * its ancestors: the navigation releases presenters in descending id order, leaves first. A place is an immutable
 * definition shared by every application in the process.
 *
 * [placeName] is what the address carries, given as is (it need not repeat the parent's name).
 */
@OptIn(ExperimentalAtomicApi::class)
class CubeTreePlace private constructor(
    override val placeName: String,
    /** The place this one is nested in; `null` for [ROOT]. */
    val parent: CubeTreePlace?,
    private val factory: ((CubeApplication) -> CubePresenter)?,
) : CubePlace {

    init {
        if (parent != null) {
            require(placeName.isNotBlank() && placeName.none { it in "?&#=" }) {
                "A place name cannot be blank or contain '?', '&', '#' or '=': '$placeName'"
            }
        }
    }

    /** Unique, growing from the root to the leaves; `-1` for [ROOT]. */
    override val id: Int = if (parent == null) -1 else lastId.incrementAndFetch()

    /** The places from the top down to this one, [ROOT] left out: the steps of the navigation that reaches it. */
    val path: List<CubeTreePlace> = if (parent == null) emptyList() else parent.path + this

    /** Whether this is [ROOT]. */
    val isRoot: Boolean get() = parent == null

    /** A place nested in this one, whose presenter [factory] builds for an application of type [A]. */
    fun <A : CubeApplication> child(placeName: String, factory: (A) -> CubePresenter): CubeTreePlace =
        CubeTreePlace(placeName, this, typed(factory))

    /** Whether [other] is this place or nested somewhere below it. */
    operator fun contains(other: CubeTreePlace): Boolean = isRoot || other.path.getOrNull(path.size - 1) === this

    @Suppress("UNCHECKED_CAST")
    override fun <A : CubeApplication> presenterFactory(): (A) -> CubePresenter =
        (factory ?: throw IllegalStateException("The root place has no presenter: the application is the root"))
            as (A) -> CubePresenter

    override fun toString(): String = placeName

    companion object {
        private val lastId = AtomicInt(-1)

        /**
         * The root of every tree: the application itself. No presenter, no step of any path; its name is empty.
         * Navigating to it runs only the application's `applyParameters`, with `deepest = true`.
         */
        val ROOT: CubeTreePlace = CubeTreePlace("", null, null)

        @Suppress("UNCHECKED_CAST")
        private fun <A : CubeApplication> typed(factory: (A) -> CubePresenter): (CubeApplication) -> CubePresenter =
            { app -> factory(app as A) }
    }
}
