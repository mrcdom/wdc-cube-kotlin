package br.com.wdc.framework.cube

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

/**
 * A place in a tree: it knows its parent, so the [path] from the root to it is the navigation that reaches it —
 * [CubeApplication.flip] steps through it.
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
    /** The place this one is nested in; `null` for a root. */
    val parent: CubeTreePlace?,
    private val factory: (CubeApplication) -> CubePresenter,
) : CubePlace {

    init {
        require(placeName.isNotBlank() && placeName.none { it in "?&#=" }) {
            "A place name cannot be blank or contain '?', '&', '#' or '=': '$placeName'"
        }
    }

    override val id: Int = lastId.incrementAndFetch()

    /** The places from the root down to this one. */
    val path: List<CubeTreePlace> = (parent?.path ?: emptyList()) + this

    /** A place nested in this one, whose presenter [factory] builds for an application of type [A]. */
    fun <A : CubeApplication> child(placeName: String, factory: (A) -> CubePresenter): CubeTreePlace =
        CubeTreePlace(placeName, this, typed(factory))

    /** Whether [other] is this place or nested somewhere below it. */
    operator fun contains(other: CubeTreePlace): Boolean = other.path.getOrNull(path.size - 1) === this

    @Suppress("UNCHECKED_CAST")
    override fun <A : CubeApplication> presenterFactory(): (A) -> CubePresenter = factory as (A) -> CubePresenter

    override fun toString(): String = placeName

    companion object {
        private val lastId = AtomicInt(-1)

        /** A root place: the first step of every path below it. */
        fun <A : CubeApplication> root(placeName: String, factory: (A) -> CubePresenter): CubeTreePlace =
            CubeTreePlace(placeName, null, typed(factory))

        @Suppress("UNCHECKED_CAST")
        private fun <A : CubeApplication> typed(factory: (A) -> CubePresenter): (CubeApplication) -> CubePresenter =
            { app -> factory(app as A) }
    }
}
