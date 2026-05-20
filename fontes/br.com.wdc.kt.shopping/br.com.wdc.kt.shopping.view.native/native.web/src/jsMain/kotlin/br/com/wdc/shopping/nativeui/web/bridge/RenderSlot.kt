package br.com.wdc.shopping.nativeui.web.bridge

import br.com.wdc.framework.cube.CubeView
import react.FC
import react.Props
import react.useEffect
import react.useState

/**
 * Renders a [CubeView] slot by casting to [ReactCubeView] and mounting
 * its React component with automatic re-render on presenter updates.
 */
val RenderSlot = FC<RenderSlotProps> { props ->
    val view = props.view as? ReactCubeView ?: return@FC
    var rev by useState(view.revision)

    useEffect(view) {
        view.onUpdate = { rev = view.revision }
    }

    // Read rev to subscribe to updates
    @Suppress("UNUSED_VARIABLE")
    val unused = rev

    view.component {}
}

external interface RenderSlotProps : Props {
    var view: CubeView?
}
