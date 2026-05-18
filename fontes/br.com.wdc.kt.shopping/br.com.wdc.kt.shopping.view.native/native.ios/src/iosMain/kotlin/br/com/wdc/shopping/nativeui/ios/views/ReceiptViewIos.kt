package br.com.wdc.shopping.nativeui.ios.views

import br.com.wdc.shopping.nativeui.ios.AbstractViewIos
import br.com.wdc.shopping.nativeui.ios.UIK
import br.com.wdc.shopping.nativeui.ios.UIKitDom
import br.com.wdc.shopping.nativeui.ios.ShoppingColors
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

        backButton = button("← Produtos") {
            setTitleColor(ShoppingColors.Primary, forState = UIControlStateNormal)
            titleLabel?.font = UIFont.systemFontOfSize(16.0)
            addTarget(actions, action = sel_registerName("onBack"), forControlEvents = UIControlEventTouchUpInside)
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(root.topAnchor, 8.0),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor, 12.0)
            ))
        }

        scrollView = scrollView(configure = {
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(backButton.bottomAnchor, 8.0),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor),
                bottomAnchor.constraintEqualToAnchor(root.bottomAnchor)
            ))
        }) {
            contentStack = vStack(spacing = 16.0) {
                // Success banner (hidden by default)
                successBanner = view(configure = {
                    backgroundColor = ShoppingColors.SuccessContainer
                    layer.cornerRadius = 8.0
                    hidden = true
                }) {
                    val successLabel = label {
                        text = "✅ Compra realizada com sucesso!"
                        font = UIFont.boldSystemFontOfSize(15.0)
                        textColor = ShoppingColors.SuccessColor
                        textAlignment = UIK.TextAlignCenter
                    }
                    pin(successLabel, insets = 12.0)
                }

                // Header
                label {
                    text = "🧾 Comprovante"
                    font = UIFont.boldSystemFontOfSize(20.0)
                    textColor = ShoppingColors.OnSurface
                }

                // Date
                dateLabel = label {
                    font = UIFont.systemFontOfSize(14.0)
                    textColor = ShoppingColors.OnSurfaceVariant
                }

                // Items list
                itemsStack = vStack(spacing = 8.0) {}

                // Total
                vStack(configure = {
                    layoutMarginsRelativeArrangement = true
                    layoutMargins = UIEdgeInsetsMake(10.0, 16.0, 10.0, 16.0)
                    backgroundColor = ShoppingColors.PriceBackground
                    layer.cornerRadius = 10.0
                }) {
                    totalLabel = label {
                        font = UIFont.boldSystemFontOfSize(22.0)
                        textColor = ShoppingColors.PriceColor
                        textAlignment = UIK.TextAlignCenter
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
            // Date
            val date = receipt.date
            dateLabel.text = if (date != null) "Data: ${ViewUtils.formatDate(date)}" else ""

            // Items
            itemsStack.arrangedSubviews.forEach { (it as? UIView)?.removeFromSuperview() }
            for (item in receipt.items) {
                val row = createReceiptItemRow(item)
                itemsStack.addArrangedSubview(row)
            }

            // Total
            val total = receipt.total
            totalLabel.text = if (total != null) "Total: R$ ${ViewUtils.formatPrice(total)}" else ""
        }
    }

    private fun createReceiptItemRow(item: ReceiptItem): UIView = UIKitDom.build {
        val row = parent()
        size(row, height = 32.0)

        val descLabel = label {
            text = "${item.description ?: ""} (×${item.quantity})"
            font = UIFont.systemFontOfSize(14.0)
            textColor = ShoppingColors.OnSurface
        }

        val valueLabel = label {
            text = "R$ ${ViewUtils.formatPrice(item.value * item.quantity)}"
            font = UIFont.boldSystemFontOfSize(14.0)
            textColor = ShoppingColors.PriceColor
            textAlignment = UIK.TextAlignRight
        }

        NSLayoutConstraint.activateConstraints(listOf(
            descLabel.leadingAnchor.constraintEqualToAnchor(row.leadingAnchor),
            descLabel.centerYAnchor.constraintEqualToAnchor(row.centerYAnchor),
            descLabel.trailingAnchor.constraintLessThanOrEqualToAnchor(valueLabel.leadingAnchor, -8.0),
            valueLabel.trailingAnchor.constraintEqualToAnchor(row.trailingAnchor),
            valueLabel.centerYAnchor.constraintEqualToAnchor(row.centerYAnchor)
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
