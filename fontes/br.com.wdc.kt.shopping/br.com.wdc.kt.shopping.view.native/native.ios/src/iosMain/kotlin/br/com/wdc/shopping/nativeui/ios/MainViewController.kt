package br.com.wdc.shopping.nativeui.ios

import platform.UIKit.UIViewController
import platform.UIKit.UILabel
import platform.UIKit.UIView
import platform.UIKit.NSLayoutConstraint
import kotlinx.cinterop.useContents

fun MainViewController(): UIViewController {
    val viewController = UIViewController()

    viewController.view.backgroundColor = platform.UIKit.UIColor.whiteColor

    val label = UILabel().apply {
        text = "Shopping — Native iOS"
        translatesAutoresizingMaskIntoConstraints = false
    }

    viewController.view.addSubview(label)

    NSLayoutConstraint.activateConstraints(listOf(
        label.centerXAnchor.constraintEqualToAnchor(viewController.view.centerXAnchor),
        label.centerYAnchor.constraintEqualToAnchor(viewController.view.centerYAnchor)
    ))

    return viewController
}
