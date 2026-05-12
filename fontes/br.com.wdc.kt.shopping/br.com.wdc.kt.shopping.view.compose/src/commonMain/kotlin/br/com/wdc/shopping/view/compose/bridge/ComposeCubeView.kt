package br.com.wdc.shopping.view.compose.bridge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import br.com.wdc.framework.cube.CubeView

/**
 * Base class for Compose-backed CubeView implementations.
 *
 * Coalesces multiple update() calls within the same cycle into a single
 * Compose recomposition by incrementing a revision counter. Compose only
 * reads the revision state during recomposition, so multiple increments
 * between frames result in a single recomposition.
 */
abstract class ComposeCubeView(
    private val id: String
) : CubeView {

    /** Revision counter — read this in @Composable functions to trigger recomposition. */
    val revision: MutableState<Int> = mutableIntStateOf(0)

    override fun instanceId(): String = id

    override fun update() {
        revision.value++
    }

    @Composable
    abstract fun Render()

    override fun release() {
        // No-op by default; Compose manages its own lifecycle
    }
}

/**
 * Renders a CubeView slot by casting to ComposeCubeView and calling Render().
 */
@Composable
fun RenderSlot(view: CubeView) {
    (view as? ComposeCubeView)?.Render()
}
