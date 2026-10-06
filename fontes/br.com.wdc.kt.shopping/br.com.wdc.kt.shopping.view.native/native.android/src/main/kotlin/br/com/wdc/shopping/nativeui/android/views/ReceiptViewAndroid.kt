package br.com.wdc.shopping.nativeui.android.views

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import br.com.wdc.shopping.nativeui.android.theme.*
import br.com.wdc.shopping.nativeui.android.toolkit.AbstractViewAndroid
import br.com.wdc.shopping.nativeui.android.toolkit.AndroidDom
import br.com.wdc.shopping.nativeui.android.toolkit.ViewUtils
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.ReceiptPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.structs.ReceiptItem

class ReceiptViewAndroid(presenter: ReceiptPresenter) : AbstractViewAndroid<ReceiptPresenter>("receipt-view", presenter) {

    private lateinit var successBanner: LinearLayout
    private lateinit var dateLabel: TextView
    private lateinit var itemsStack: LinearLayout
    private lateinit var totalLabel: TextView

    private var successShown = false

    override fun createView(): View {
        val ctx = RootViewAndroid.appContext
        val density = ctx.resources.displayMetrics.density

        return AndroidDom.build(ctx) {
            val root = parent()
            root.setBackgroundColor(ShoppingColors.Background)

            scrollView {
                vStack(spacing = (16 * density).toInt(), configure = {
                    setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
                }) {
                    // Success banner
                    successBanner = hStack(configure = {
                        background = ShoppingStyles.roundedBackground(ShoppingColors.SuccessContainer, 12 * density)
                        gravity = Gravity.CENTER
                        setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
                        visibility = View.GONE
                    }) {
                        textView {
                            text = "✅ Compra realizada com sucesso!"
                            textSize = 15f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(ShoppingColors.SuccessColor)
                            gravity = Gravity.CENTER
                        }
                    }

                    // Header: icon + "Recibo" + date badge
                    hStack(configure = {
                        gravity = Gravity.CENTER_VERTICAL
                    }) {
                        imageView {
                            setImageDrawable(ShoppingIcons.inventory(ctx, 22, ShoppingColors.OnSurface))
                            (layoutParams as LinearLayout.LayoutParams).apply {
                                width = (22 * density).toInt()
                                height = (22 * density).toInt()
                                marginEnd = (8 * density).toInt()
                            }
                        }
                        textView {
                            text = "Recibo"
                            textSize = 24f
                            setTextColor(ShoppingColors.OnSurface)
                            (layoutParams as LinearLayout.LayoutParams).apply {
                                weight = 1f
                                width = 0
                            }
                        }
                        dateLabel = textView {
                            textSize = 13f
                            setTextColor(ShoppingColors.OnSurfaceVariant)
                            gravity = Gravity.CENTER
                            background = ShoppingStyles.roundedBackground(ShoppingColors.SurfaceVariant, 8 * density)
                            val padH = (8 * density).toInt()
                            val padV = (4 * density).toInt()
                            setPadding(padH, padV, padH, padV)
                            (layoutParams as LinearLayout.LayoutParams).width = ViewGroup.LayoutParams.WRAP_CONTENT
                        }
                    }

                    // Separator
                    frame(configure = {
                        setBackgroundColor(ShoppingColors.SurfaceVariant)
                        (layoutParams as LinearLayout.LayoutParams).height = 1
                    })

                    // Column headers
                    hStack(configure = {
                        gravity = Gravity.CENTER_VERTICAL
                    }) {
                        textView {
                            text = "Item"
                            textSize = 12f
                            setTextColor(ShoppingColors.OnSurfaceVariant)
                            (layoutParams as LinearLayout.LayoutParams).apply {
                                weight = 1f
                                width = 0
                            }
                        }
                        textView {
                            text = "Qtd"
                            textSize = 12f
                            setTextColor(ShoppingColors.OnSurfaceVariant)
                            gravity = Gravity.CENTER
                            (layoutParams as LinearLayout.LayoutParams).width = (50 * density).toInt()
                        }
                        textView {
                            text = "Valor"
                            textSize = 12f
                            setTextColor(ShoppingColors.OnSurfaceVariant)
                            gravity = Gravity.END
                            (layoutParams as LinearLayout.LayoutParams).width = (90 * density).toInt()
                        }
                    }

                    // Items list
                    itemsStack = vStack(spacing = (8 * density).toInt()) {}

                    // Separator before total
                    frame(configure = {
                        setBackgroundColor(ShoppingColors.SurfaceVariant)
                        (layoutParams as LinearLayout.LayoutParams).height = 1
                    })

                    // Total row
                    hStack(configure = {
                        gravity = Gravity.CENTER_VERTICAL
                    }) {
                        textView {
                            text = "Total:"
                            textSize = 15f
                            setTextColor(ShoppingColors.OnSurfaceVariant)
                            (layoutParams as LinearLayout.LayoutParams).apply {
                                width = ViewGroup.LayoutParams.WRAP_CONTENT
                                marginEnd = (8 * density).toInt()
                            }
                        }
                        frame(configure = {
                            background = ShoppingStyles.roundedBackground(ShoppingColors.PriceBackground, 10 * density)
                            setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
                            (layoutParams as LinearLayout.LayoutParams).width = ViewGroup.LayoutParams.WRAP_CONTENT
                        }) {
                            totalLabel = textView {
                                textSize = 20f
                                setTypeface(null, Typeface.BOLD)
                                setTextColor(ShoppingColors.PriceColor)
                                gravity = Gravity.CENTER
                                (layoutParams as FrameLayout.LayoutParams).width = ViewGroup.LayoutParams.WRAP_CONTENT
                            }
                        }
                    }

                    // Back button (right-aligned)
                    hStack {
                        flexSpacer()
                        button("Continuar Comprando") {
                            textSize = 15f
                            setTextColor(android.graphics.Color.WHITE)
                            background = ShoppingStyles.roundedBackground(ShoppingColors.Primary, 12 * density)
                            val padH = (20 * density).toInt()
                            val padV = (8 * density).toInt()
                            setPadding(padH, padV, padH, padV)
                            setCompoundDrawablesRelativeWithIntrinsicBounds(
                                ShoppingIcons.arrowBack(ctx, 18, android.graphics.Color.WHITE), null, null, null
                            )
                            compoundDrawablePadding = (4 * density).toInt()
                            (layoutParams as LinearLayout.LayoutParams).apply {
                                width = ViewGroup.LayoutParams.WRAP_CONTENT
                                height = (48 * density).toInt()
                            }
                            setOnClickListener { safeAction("back") { presenter.onOpenProducts() } }
                        }
                    }
                }
            }
        }
    }

    override fun doUpdate() {
        val state = presenter.state
        val receipt = state.receipt

        // Success banner
        if (state.notifySuccess && !successShown) {
            successBanner.visibility = View.VISIBLE
            successShown = true
        }

        if (receipt != null) {
            val date = receipt.date
            dateLabel.text = if (date != null) ViewUtils.formatDate(date) else ""

            // Rebuild items rows
            itemsStack.removeAllViews()
            for (item in receipt.items) {
                itemsStack.addView(createReceiptItemRow(item))
            }

            val total = receipt.total ?: receipt.items.sumOf { it.value * it.quantity }
            totalLabel.text = "R$ ${ViewUtils.formatPrice(total)}"
        }
    }

    private fun createReceiptItemRow(item: ReceiptItem): View {
        val ctx = RootViewAndroid.appContext
        val density = ctx.resources.displayMetrics.density

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ShoppingStyles.roundedBackground(ShoppingColors.SurfaceVariant40, 8 * density)
            setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // Description
        val descTv = TextView(ctx).apply {
            text = item.description ?: ""
            textSize = 14f
            setTextColor(ShoppingColors.OnSurface)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(descTv)

        // Quantity
        val qtyTv = TextView(ctx).apply {
            text = "${item.quantity}x"
            textSize = 14f
            setTextColor(ShoppingColors.OnSurfaceVariant)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((50 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        row.addView(qtyTv)

        // Value
        val valueTv = TextView(ctx).apply {
            text = "R$ ${ViewUtils.formatPrice(item.value)}"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ShoppingColors.PriceColor)
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams((90 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        row.addView(valueTv)

        return row
    }
}
