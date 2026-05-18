package br.com.wdc.shopping.nativeui.ios.views

import br.com.wdc.shopping.nativeui.ios.AbstractViewIos
import br.com.wdc.shopping.nativeui.ios.UIK
import br.com.wdc.shopping.nativeui.ios.UIKitDom
import br.com.wdc.shopping.nativeui.ios.ShoppingColors
import br.com.wdc.shopping.nativeui.ios.ShoppingIcons
import br.com.wdc.shopping.nativeui.ios.ViewUtils
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.ReceiptPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.structs.ReceiptItem
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.UIKit.*
import platform.objc.sel_registerName
import platform.darwin.NSObject

/**
 * Receipt view — shows purchase confirmation with success banner, item list, and total.
 */
@OptIn(ExperimentalForeignApi::class)
class ReceiptViewIos(presenter: ReceiptPresenter) : AbstractViewIos<ReceiptPresenter>("receipt-view", presenter) {

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
        root.backgroundColor = ShoppingColors.Background

        scrollView = scrollView(configure = {
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(root.topAnchor),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor),
                bottomAnchor.constraintEqualToAnchor(root.bottomAnchor)
            ))
        }) {
            contentStack = vStack(spacing = 16.0) {
                // Success banner (hidden by default)
                successBanner = view(configure = {
                    backgroundColor = ShoppingColors.SuccessContainer
                    layer.cornerRadius = 12.0
                    hidden = true
                }) {
                    val successLabel = label {
                        text = "✅ Compra realizada com sucesso!"
                        font = UIFont.boldSystemFontOfSize(15.0)
                        textColor = ShoppingColors.SuccessColor
                        textAlignment = UIK.TextAlignCenter
                    }
                    pin(successLabel, insets = 16.0)
                }

                // Header row: title + date badge
                hStack(spacing = 8.0, configure = { alignment = UIK.StackAlignCenter }) {
                    imageView {
                        image = ShoppingIcons.receipt(22.0, ShoppingColors.OnSurface)
                        contentMode = UIK.ContentModeScaleAspectFit
                        NSLayoutConstraint.activateConstraints(listOf(
                            widthAnchor.constraintEqualToConstant(22.0),
                            heightAnchor.constraintEqualToConstant(22.0)
                        ))
                    }
                    label {
                        text = "Recibo"
                        font = UIFont.systemFontOfSize(24.0, UIFontWeightMedium)
                        textColor = ShoppingColors.OnSurface
                    }
                    flexSpacer()
                    dateLabel = label {
                        font = UIFont.systemFontOfSize(13.0)
                        textColor = ShoppingColors.OnSurfaceVariant
                        backgroundColor = ShoppingColors.SurfaceVariant
                        textAlignment = UIK.TextAlignCenter
                        layer.cornerRadius = 8.0
                        clipsToBounds = true
                    }
                }

                // Separator
                view(configure = {
                    backgroundColor = ShoppingColors.SurfaceVariant
                    heightAnchor.constraintEqualToConstant(1.0).active = true
                })

                // Column headers
                hStack(spacing = 0.0) {
                    label {
                        text = "Item"
                        font = UIFont.systemFontOfSize(12.0)
                        textColor = ShoppingColors.OnSurfaceVariant
                        setContentHuggingPriority(249.0f, UIK.AxisHorizontal)
                    }
                    label {
                        text = "Qtd"
                        font = UIFont.systemFontOfSize(12.0)
                        textColor = ShoppingColors.OnSurfaceVariant
                        textAlignment = UIK.TextAlignCenter
                        widthAnchor.constraintEqualToConstant(50.0).active = true
                    }
                    label {
                        text = "Valor"
                        font = UIFont.systemFontOfSize(12.0)
                        textColor = ShoppingColors.OnSurfaceVariant
                        textAlignment = UIK.TextAlignRight
                        widthAnchor.constraintEqualToConstant(90.0).active = true
                    }
                }

                // Items list
                itemsStack = vStack(spacing = 8.0) {}

                // Separator before total
                view(configure = {
                    backgroundColor = ShoppingColors.SurfaceVariant
                    heightAnchor.constraintEqualToConstant(1.0).active = true
                })

                // Total in badge
                hStack(spacing = 8.0, configure = { alignment = UIK.StackAlignCenter }) {
                    label {
                        text = "Total:"
                        font = UIFont.systemFontOfSize(15.0)
                        textColor = ShoppingColors.OnSurfaceVariant
                    }
                    view(configure = {
                        backgroundColor = ShoppingColors.PriceBackground
                        layer.cornerRadius = 10.0
                    }) {
                        totalLabel = label {
                            font = UIFont.boldSystemFontOfSize(20.0)
                            textColor = ShoppingColors.PriceColor
                            textAlignment = UIK.TextAlignCenter
                        }
                        pin(totalLabel, insets = 8.0)
                    }
                }

                // Back button
                hStack(spacing = 0.0) {
                    flexSpacer()
                    backButton = button("Continuar Comprando") {
                        setTitleColor(UIColor.whiteColor, forState = UIControlStateNormal)
                        backgroundColor = ShoppingColors.Primary
                        layer.cornerRadius = 12.0
                        titleLabel?.font = UIFont.systemFontOfSize(15.0, UIFontWeightMedium)
                        setImage(ShoppingIcons.arrowBack(18.0, UIColor.whiteColor), forState = UIControlStateNormal)
                        imageEdgeInsets = UIEdgeInsetsMake(0.0, -4.0, 0.0, 4.0)
                        contentEdgeInsets = UIEdgeInsetsMake(8.0, 20.0, 8.0, 20.0)
                        addTarget(actions, action = sel_registerName("onBack"), forControlEvents = UIControlEventTouchUpInside)
                        NSLayoutConstraint.activateConstraints(listOf(
                            heightAnchor.constraintEqualToConstant(48.0)
                        ))
                    }
                }
            }
            NSLayoutConstraint.activateConstraints(listOf(
                contentStack.topAnchor.constraintEqualToAnchor(parent().topAnchor, 16.0),
                contentStack.leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor, 16.0),
                contentStack.trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor, -16.0),
                contentStack.bottomAnchor.constraintEqualToAnchor(parent().bottomAnchor, -16.0),
                contentStack.widthAnchor.constraintEqualToAnchor(parent().widthAnchor, constant = -32.0)
            ))
        }
    }

    override fun doUpdate() {
        val state = presenter.state
        val receipt = state.receipt

        // Success banner
        if (state.notifySuccess && !successShown) {
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
            totalLabel.text = if (total != null) "R$ ${ViewUtils.formatPrice(total)}" else ""
        }
    }

    private fun createReceiptItemRow(item: ReceiptItem): UIView = UIKitDom.build {
        val row = parent()
        row.backgroundColor = ShoppingColors.SurfaceVariant40
        row.layer.cornerRadius = 8.0

        val descLabel = label {
            text = item.description ?: ""
            font = UIFont.systemFontOfSize(14.0, UIFontWeightMedium)
            textColor = ShoppingColors.OnSurface
            setContentHuggingPriority(249.0f, UIK.AxisHorizontal)
        }

        val qtyLabel = label {
            text = "${item.quantity}x"
            font = UIFont.systemFontOfSize(14.0)
            textColor = ShoppingColors.OnSurfaceVariant
            textAlignment = UIK.TextAlignCenter
            widthAnchor.constraintEqualToConstant(50.0).active = true
        }

        val valueLabel = label {
            text = "R$ ${ViewUtils.formatPrice(item.value)}"
            font = UIFont.systemFontOfSize(14.0, UIFontWeightMedium)
            textColor = ShoppingColors.PriceColor
            textAlignment = UIK.TextAlignRight
            widthAnchor.constraintEqualToConstant(90.0).active = true
        }

        NSLayoutConstraint.activateConstraints(listOf(
            descLabel.leadingAnchor.constraintEqualToAnchor(row.leadingAnchor, 12.0),
            descLabel.centerYAnchor.constraintEqualToAnchor(row.centerYAnchor),
            descLabel.trailingAnchor.constraintEqualToAnchor(qtyLabel.leadingAnchor, -4.0),
            qtyLabel.centerYAnchor.constraintEqualToAnchor(row.centerYAnchor),
            qtyLabel.trailingAnchor.constraintEqualToAnchor(valueLabel.leadingAnchor, -4.0),
            valueLabel.trailingAnchor.constraintEqualToAnchor(row.trailingAnchor, -12.0),
            valueLabel.centerYAnchor.constraintEqualToAnchor(row.centerYAnchor),
            row.heightAnchor.constraintEqualToConstant(40.0)
        ))
    }

    internal fun goBack() {
        presenter.onOpenProducts()
    }
}

@OptIn(ExperimentalForeignApi::class)
private class ReceiptActions(private val view: ReceiptViewIos) : NSObject() {

    @ObjCAction
    fun onBack() {
        view.goBack()
    }
}
