package br.com.wdc.store.nativeui.web.bridge

import br.com.wdc.framework.cube.CubeView
import react.FC
import react.Props
import react.memo

/**
 * Renders a [CubeView] slot by mounting the React component of its [ReactCubeView].
 *
 * **Memoized: a parent repaint does not repaint the slot.** Cube views are independent of each other; a view's
 * content only changes when its own scope does, and then the view repaints itself (each component
 * subscribes to its view's `onUpdate`). The slot repaints only when the parent puts a different view in it.
 */
val RenderSlot = memo(FC<RenderSlotProps> { props ->
    val view = props.view as? ReactCubeView<*> ?: return@FC
    view.component {}
})

external interface RenderSlotProps : Props {
    var view: CubeView?
}
