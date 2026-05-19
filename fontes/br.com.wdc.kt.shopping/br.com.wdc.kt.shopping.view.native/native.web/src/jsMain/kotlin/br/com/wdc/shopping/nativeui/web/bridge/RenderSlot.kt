package br.com.wdc.shopping.nativeui.web.bridge

import react.FC
import react.Props
import react.useEffect
import react.useState

/**
 * Renders a child [ReactCubeView] by reference.
 * Subscribes to revision changes and re-renders accordingly.
 */
val RenderSlot = FC<RenderSlotProps> { props ->
    val view = props.view ?: return@FC
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
    var view: ReactCubeView?
}
