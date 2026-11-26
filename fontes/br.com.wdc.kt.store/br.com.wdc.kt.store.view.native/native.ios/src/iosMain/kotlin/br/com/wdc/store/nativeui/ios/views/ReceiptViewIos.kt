package br.com.wdc.store.nativeui.ios.views

import br.com.wdc.store.design.StoreTokens.FontSize
import br.com.wdc.store.design.StoreTokens.Radius
import br.com.wdc.store.design.StoreTokens.Spacing
import br.com.wdc.store.nativeui.ios.theme.pt
import br.com.wdc.store.nativeui.ios.toolkit.AbstractViewIos
import br.com.wdc.store.nativeui.ios.toolkit.UIKitDom
import br.com.wdc.store.nativeui.ios.toolkit.ViewUtils
import br.com.wdc.store.nativeui.ios.theme.StoreColors
import br.com.wdc.store.nativeui.ios.theme.StoreIcons
import br.com.wdc.store.nativeui.ios.theme.UIK
import br.com.wdc.store.presentation.presenter.restricted.receipt.ReceiptScope
import br.com.wdc.store.nativeui.ios.IosNativeRootPresenter
import br.com.wdc.store.presentation.presenter.restricted.receipt.structs.ReceiptItem
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.UIKit.*
import platform.objc.sel_registerName
import platform.darwin.NSObject

/**
 * Receipt view — shows purchase confirmation with success banner, item list, and total.
 */
@OptIn(ExperimentalForeignApi::class)
class ReceiptViewIos(app: IosNativeRootPresenter, scope: ReceiptScope) : AbstractViewIos<ReceiptScope>("receipt-view", app, scope) {

    private lateinit var scrollView: UIScrollView
    private lateinit var contentStack: UIStackView
    private lateinit var successBanner: UIView
    private lateinit var dateLabel: UILabel
    private lateinit var itemsStack: UIStackView
    private lateinit var totalLabel: UILabel
    private lateinit var backButton: UIButton

    private val actions = ReceiptActions(this)
    private var successShown = false

    override fun createView(): UIView = UIKitDom.build {
        val root = parent()
        root.backgroundColor = StoreColors.Background

        scrollView = scrollView(configure = {
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(root.topAnchor),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor),
                bottomAnchor.constraintEqualToAnchor(root.bottomAnchor)
            ))
        }) {
            contentStack = vStack(spacing = Spacing.Lg.pt) {
                // Success banner (hidden by default)
                successBanner = view(configure = {
                    backgroundColor = StoreColors.SuccessContainer
                    layer.cornerRadius = Radius.Md.pt
                    hidden = true
                }) {
                    val successLabel = label {
                        text = "✅ Purchase completed successfully!"
                        font = UIFont.boldSystemFontOfSize(15.0)
                        textColor = StoreColors.SuccessColor
                        textAlignment = UIK.TextAlignCenter
                    }
                    pin(successLabel, insets = Spacing.Lg.pt)
                }

                // Header row: title + date badge
                hStack(spacing = Spacing.Sm.pt, configure = { alignment = UIK.StackAlignCenter }) {
                    imageView {
                        image = StoreIcons.receipt(22.0, StoreColors.OnSurface)
                        contentMode = UIK.ContentModeScaleAspectFit
                        NSLayoutConstraint.activateConstraints(listOf(
                            widthAnchor.constraintEqualToConstant(22.0),
                            heightAnchor.constraintEqualToConstant(22.0)
                        ))
                    }
                    label {
                        text = "Receipt"
                        font = UIFont.systemFontOfSize(FontSize.Headline.pt, UIFontWeightMedium)
                        textColor = StoreColors.OnSurface
                    }
                    flexSpacer()
                    dateLabel = label {
                        font = UIFont.systemFontOfSize(13.0)
                        textColor = StoreColors.OnSurfaceVariant
                        backgroundColor = StoreColors.SurfaceVariant
                        textAlignment = UIK.TextAlignCenter
                        layer.cornerRadius = Radius.Sm.pt
                        clipsToBounds = true
                    }
                }

                // Separator
                view(configure = {
                    backgroundColor = StoreColors.SurfaceVariant
                    heightAnchor.constraintEqualToConstant(1.0).active = true
                })

                // Column headers
                hStack(spacing = 0.0) {
                    label {
                        text = "Item"
                        font = UIFont.systemFontOfSize(FontSize.Caption.pt)
                        textColor = StoreColors.OnSurfaceVariant
                        setContentHuggingPriority(249.0f, UIK.AxisHorizontal)
                    }
                    label {
                        text = "Qty"
                        font = UIFont.systemFontOfSize(FontSize.Caption.pt)
                        textColor = StoreColors.OnSurfaceVariant
                        textAlignment = UIK.TextAlignCenter
                        widthAnchor.constraintEqualToConstant(50.0).active = true
                    }
                    label {
                        text = "Amount"
                        font = UIFont.systemFontOfSize(FontSize.Caption.pt)
                        textColor = StoreColors.OnSurfaceVariant
                        textAlignment = UIK.TextAlignRight
                        widthAnchor.constraintEqualToConstant(90.0).active = true
                    }
                }

                // Items list
                itemsStack = vStack(spacing = Spacing.Sm.pt) {}

                // Separator before total
                view(configure = {
                    backgroundColor = StoreColors.SurfaceVariant
                    heightAnchor.constraintEqualToConstant(1.0).active = true
                })

                // Total in badge
                hStack(spacing = Spacing.Sm.pt, configure = { alignment = UIK.StackAlignCenter }) {
                    label {
                        text = "Total:"
                        font = UIFont.systemFontOfSize(15.0)
                        textColor = StoreColors.OnSurfaceVariant
                    }
                    view(configure = {
                        backgroundColor = StoreColors.PriceBackground
                        layer.cornerRadius = 10.0
                    }) {
                        totalLabel = label {
                            font = UIFont.boldSystemFontOfSize(FontSize.Title.pt)
                            textColor = StoreColors.PriceColor
                            textAlignment = UIK.TextAlignCenter
                        }
                        pin(totalLabel, insets = Spacing.Sm.pt)
                    }
                }

                // Back button
                hStack(spacing = 0.0) {
                    flexSpacer()
                    backButton = button("Continue Shopping") {
                        setTitleColor(UIColor.whiteColor, forState = UIControlStateNormal)
                        backgroundColor = StoreColors.Primary
                        layer.cornerRadius = Radius.Md.pt
                        titleLabel?.font = UIFont.systemFontOfSize(15.0, UIFontWeightMedium)
                        setImage(StoreIcons.arrowBack(18.0, UIColor.whiteColor), forState = UIControlStateNormal)
                        imageEdgeInsets = UIEdgeInsetsMake(0.0, -Spacing.Xs.pt, 0.0, Spacing.Xs.pt)
                        contentEdgeInsets = UIEdgeInsetsMake(Spacing.Sm.pt, 20.0, Spacing.Sm.pt, 20.0)
                        addTarget(actions, action = sel_registerName("onBack"), forControlEvents = UIControlEventTouchUpInside)
                        NSLayoutConstraint.activateConstraints(listOf(
                            heightAnchor.constraintEqualToConstant(48.0)
                        ))
                    }
                }
            }
            NSLayoutConstraint.activateConstraints(listOf(
                contentStack.topAnchor.constraintEqualToAnchor(parent().topAnchor, Spacing.Lg.pt),
                contentStack.leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor, Spacing.Lg.pt),
                contentStack.trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor, -Spacing.Lg.pt),
                contentStack.bottomAnchor.constraintEqualToAnchor(parent().bottomAnchor, -Spacing.Lg.pt),
                contentStack.widthAnchor.constraintEqualToAnchor(parent().widthAnchor, constant = -32.0)
            ))
        }
    }

    override fun doUpdate() {
        val receipt = scope.receipt

        // Success banner
        if (scope.notifySuccess && !successShown) {
            successBanner.hidden = false
            successShown = true
        }

        if (receipt != null) {
            // Date in badge format
            val date = receipt.date
            dateLabel.text = if (date != null) "  ${ViewUtils.formatDate(date)}  " else ""

            // Items
            itemsStack.arrangedSubviews.forEach { (it as? UIView)?.removeFromSuperview() }
            for (item in receipt.items) {
                val row = createReceiptItemRow(item)
                itemsStack.addArrangedSubview(row)
            }

            // Total
            val total = receipt.total
            totalLabel.text = if (total != null) "\$${ViewUtils.formatPrice(total)}" else ""
        }
    }

    private fun createReceiptItemRow(item: ReceiptItem): UIView = UIKitDom.build {
        val row = parent()
        row.backgroundColor = StoreColors.SurfaceVariant40
        row.layer.cornerRadius = Radius.Sm.pt

        val descLabel = label {
            text = item.description ?: ""
            font = UIFont.systemFontOfSize(FontSize.Body.pt, UIFontWeightMedium)
            textColor = StoreColors.OnSurface
            setContentHuggingPriority(249.0f, UIK.AxisHorizontal)
        }

        val qtyLabel = label {
            text = "${item.quantity}x"
            font = UIFont.systemFontOfSize(FontSize.Body.pt)
            textColor = StoreColors.OnSurfaceVariant
            textAlignment = UIK.TextAlignCenter
            widthAnchor.constraintEqualToConstant(50.0).active = true
        }

        val valueLabel = label {
            text = "\$${ViewUtils.formatPrice(item.value)}"
            font = UIFont.systemFontOfSize(FontSize.Body.pt, UIFontWeightMedium)
            textColor = StoreColors.PriceColor
            textAlignment = UIK.TextAlignRight
            widthAnchor.constraintEqualToConstant(90.0).active = true
        }

        NSLayoutConstraint.activateConstraints(listOf(
            descLabel.leadingAnchor.constraintEqualToAnchor(row.leadingAnchor, Spacing.Md.pt),
            descLabel.centerYAnchor.constraintEqualToAnchor(row.centerYAnchor),
            descLabel.trailingAnchor.constraintEqualToAnchor(qtyLabel.leadingAnchor, -Spacing.Xs.pt),
            qtyLabel.centerYAnchor.constraintEqualToAnchor(row.centerYAnchor),
            qtyLabel.trailingAnchor.constraintEqualToAnchor(valueLabel.leadingAnchor, -Spacing.Xs.pt),
            valueLabel.trailingAnchor.constraintEqualToAnchor(row.trailingAnchor, -Spacing.Md.pt),
            valueLabel.centerYAnchor.constraintEqualToAnchor(row.centerYAnchor),
            row.heightAnchor.constraintEqualToConstant(40.0)
        ))
    }

    internal fun goBack() {
        safeAction("back") { scope.onOpenProducts() }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class ReceiptActions(private val view: ReceiptViewIos) : NSObject() {

    @ObjCAction
    fun onBack() {
        view.goBack()
    }
}
