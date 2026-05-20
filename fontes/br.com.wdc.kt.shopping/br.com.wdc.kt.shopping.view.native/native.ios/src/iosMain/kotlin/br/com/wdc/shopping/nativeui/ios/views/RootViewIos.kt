package br.com.wdc.shopping.nativeui.ios.views

import br.com.wdc.shopping.nativeui.ios.toolkit.*
import br.com.wdc.shopping.nativeui.ios.theme.*
import br.com.wdc.shopping.presentation.presenter.RootPresenter
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.*

/**
 * Root view — full-screen container with a single content slot.
 * Swaps between LoginView and HomeView based on presenter state.
 */
@OptIn(ExperimentalForeignApi::class)
class RootViewIos(presenter: RootPresenter) : AbstractViewIos<RootPresenter>("root-view", presenter) {

    private lateinit var contentSlot: ViewSlot

    override fun createView(): UIView = UIKitDom.build {
        val root = parent()
        root.backgroundColor = ShoppingColors.Background

        val container = view()
        pin(container)

        contentSlot = newViewSlot(container)
    }

    override fun doUpdate() {
        val state = presenter.state

        // Sync content slot
        contentSlot.sync(state.contentView)

        // Show error toast if any
        val errorMessage = state.errorMessage
        if (!errorMessage.isNullOrBlank()) {
            showErrorToast(errorMessage)
            state.errorMessage = null
        }
    }

    private fun showErrorToast(message: String) {
        val wrapper = UIKitDom.build {
            val w = parent()
            w.backgroundColor = UIColor(red = 0.2, green = 0.2, blue = 0.2, alpha = 0.9)
            w.layer.cornerRadius = 8.0
            w.clipsToBounds = true

            val toast = label {
                text = message
                textColor = UIColor.whiteColor
                textAlignment = UIK.TextAlignCenter
                font = UIFont.systemFontOfSize(14.0)
                numberOfLines = 0
            }
            pin(toast, insets = 12.0)
        }
        rootView.addSubview(wrapper)

        NSLayoutConstraint.activateConstraints(listOf(
            wrapper.bottomAnchor.constraintEqualToAnchor(rootView.bottomAnchor, -48.0),
            wrapper.leadingAnchor.constraintGreaterThanOrEqualToAnchor(rootView.leadingAnchor, 24.0),
            wrapper.trailingAnchor.constraintLessThanOrEqualToAnchor(rootView.trailingAnchor, -24.0),
            wrapper.centerXAnchor.constraintEqualToAnchor(rootView.centerXAnchor)
        ))

        // Auto-dismiss after 4 seconds
        platform.darwin.dispatch_after(
            platform.darwin.dispatch_time(platform.darwin.DISPATCH_TIME_NOW, (4_000_000_000).toLong()),
            platform.darwin.dispatch_get_main_queue()
        ) {
            UIView.animateWithDuration(0.3, animations = {
                wrapper.alpha = 0.0
            }) { _ ->
                wrapper.removeFromSuperview()
            }
        }
    }
}
