package br.com.wdc.store.nativeui.android.views

import br.com.wdc.store.design.StoreTokens
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import br.com.wdc.store.nativeui.android.theme.*
import br.com.wdc.store.nativeui.android.toolkit.AbstractViewAndroid
import br.com.wdc.store.nativeui.android.toolkit.AndroidDom
import br.com.wdc.store.nativeui.android.toolkit.ViewUtils
import br.com.wdc.store.nativeui.android.AndroidNativeRootPresenter
import br.com.wdc.store.presentation.presenter.restricted.receipt.ReceiptScope
import br.com.wdc.store.presentation.presenter.restricted.receipt.structs.ReceiptItem

class ReceiptViewAndroid(app: AndroidNativeRootPresenter, scope: ReceiptScope) : AbstractViewAndroid<ReceiptScope>("receipt-view", app, scope) {

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
            root.setBackgroundColor(StoreColors.Background)

            scrollView {
                vStack(spacing = (Dimens.spacingLg * density).toInt(), configure = {
                    setPadding((Dimens.spacingLg * density).toInt(), (Dimens.spacingLg * density).toInt(), (Dimens.spacingLg * density).toInt(), (Dimens.spacingLg * density).toInt())
                }) {
                    // Success banner
                    successBanner = hStack(configure = {
                        background = StoreStyles.roundedBackground(StoreColors.SuccessContainer, Dimens.radiusMd * density)
                        gravity = Gravity.CENTER
                        setPadding((Dimens.spacingLg * density).toInt(), (Dimens.spacingLg * density).toInt(), (Dimens.spacingLg * density).toInt(), (Dimens.spacingLg * density).toInt())
                        visibility = View.GONE
                    }) {
                        textView {
                            text = "✅ Purchase completed successfully!"
                            textSize = 15f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(StoreColors.SuccessColor)
                            gravity = Gravity.CENTER
                        }
                    }

                    // Header: icon + "Receipt" + date badge
                    hStack(configure = {
                        gravity = Gravity.CENTER_VERTICAL
                    }) {
                        imageView {
                            setImageDrawable(StoreIcons.inventory(ctx, 22, StoreColors.OnSurface))
                            (layoutParams as LinearLayout.LayoutParams).apply {
                                width = (22 * density).toInt()
                                height = (22 * density).toInt()
                                marginEnd = (Dimens.spacingSm * density).toInt()
                            }
                        }
                        textView {
                            text = "Receipt"
                            textSize = StoreTokens.FontSize.Headline.toFloat()
                            setTextColor(StoreColors.OnSurface)
                            (layoutParams as LinearLayout.LayoutParams).apply {
                                weight = 1f
                                width = 0
                            }
                        }
                        dateLabel = textView {
                            textSize = 13f
                            setTextColor(StoreColors.OnSurfaceVariant)
                            gravity = Gravity.CENTER
                            background = StoreStyles.roundedBackground(StoreColors.SurfaceVariant, Dimens.radiusSm * density)
                            val padH = (Dimens.spacingSm * density).toInt()
                            val padV = (Dimens.spacingXs * density).toInt()
                            setPadding(padH, padV, padH, padV)
                            (layoutParams as LinearLayout.LayoutParams).width = ViewGroup.LayoutParams.WRAP_CONTENT
                        }
                    }

                    // Separator
                    frame(configure = {
                        setBackgroundColor(StoreColors.SurfaceVariant)
                        (layoutParams as LinearLayout.LayoutParams).height = 1
                    })

                    // Column headers
                    hStack(configure = {
                        gravity = Gravity.CENTER_VERTICAL
                    }) {
                        textView {
                            text = "Item"
                            textSize = StoreTokens.FontSize.Caption.toFloat()
                            setTextColor(StoreColors.OnSurfaceVariant)
                            (layoutParams as LinearLayout.LayoutParams).apply {
                                weight = 1f
                                width = 0
                            }
                        }
                        textView {
                            text = "Qty"
                            textSize = StoreTokens.FontSize.Caption.toFloat()
                            setTextColor(StoreColors.OnSurfaceVariant)
                            gravity = Gravity.CENTER
                            (layoutParams as LinearLayout.LayoutParams).width = (50 * density).toInt()
                        }
                        textView {
                            text = "Amount"
                            textSize = StoreTokens.FontSize.Caption.toFloat()
                            setTextColor(StoreColors.OnSurfaceVariant)
                            gravity = Gravity.END
                            (layoutParams as LinearLayout.LayoutParams).width = (90 * density).toInt()
                        }
                    }

                    // Items list
                    itemsStack = vStack(spacing = (Dimens.spacingSm * density).toInt()) {}

                    // Separator before total
                    frame(configure = {
                        setBackgroundColor(StoreColors.SurfaceVariant)
                        (layoutParams as LinearLayout.LayoutParams).height = 1
                    })

                    // Total row
                    hStack(configure = {
                        gravity = Gravity.CENTER_VERTICAL
                    }) {
                        textView {
                            text = "Total:"
                            textSize = 15f
                            setTextColor(StoreColors.OnSurfaceVariant)
                            (layoutParams as LinearLayout.LayoutParams).apply {
                                width = ViewGroup.LayoutParams.WRAP_CONTENT
                                marginEnd = (Dimens.spacingSm * density).toInt()
                            }
                        }
                        frame(configure = {
                            background = StoreStyles.roundedBackground(StoreColors.PriceBackground, 10 * density)
                            setPadding((Dimens.spacingSm * density).toInt(), (Dimens.spacingSm * density).toInt(), (Dimens.spacingSm * density).toInt(), (Dimens.spacingSm * density).toInt())
                            (layoutParams as LinearLayout.LayoutParams).width = ViewGroup.LayoutParams.WRAP_CONTENT
                        }) {
                            totalLabel = textView {
                                textSize = StoreTokens.FontSize.Title.toFloat()
                                setTypeface(null, Typeface.BOLD)
                                setTextColor(StoreColors.PriceColor)
                                gravity = Gravity.CENTER
                                (layoutParams as FrameLayout.LayoutParams).width = ViewGroup.LayoutParams.WRAP_CONTENT
                            }
                        }
                    }

                    // Back button (right-aligned)
                    hStack {
                        flexSpacer()
                        button("Continue Shopping") {
                            textSize = 15f
                            setTextColor(android.graphics.Color.WHITE)
                            background = StoreStyles.roundedBackground(StoreColors.Primary, Dimens.radiusMd * density)
                            val padH = (20 * density).toInt()
                            val padV = (Dimens.spacingSm * density).toInt()
                            setPadding(padH, padV, padH, padV)
                            setCompoundDrawablesRelativeWithIntrinsicBounds(
                                StoreIcons.arrowBack(ctx, 18, android.graphics.Color.WHITE), null, null, null
                            )
                            compoundDrawablePadding = (Dimens.spacingXs * density).toInt()
                            (layoutParams as LinearLayout.LayoutParams).apply {
                                width = ViewGroup.LayoutParams.WRAP_CONTENT
                                height = (48 * density).toInt()
                            }
                            setOnClickListener { safeAction("back") { scope.onOpenProducts() } }
                        }
                    }
                }
            }
        }
    }

    override fun doUpdate() {
        val receipt = scope.receipt

        // Success banner
        if (scope.notifySuccess && !successShown) {
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
            totalLabel.text = "\$${ViewUtils.formatPrice(total)}"
        }
    }

    private fun createReceiptItemRow(item: ReceiptItem): View {
        val ctx = RootViewAndroid.appContext
        val density = ctx.resources.displayMetrics.density

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = StoreStyles.roundedBackground(StoreColors.SurfaceVariant40, Dimens.radiusSm * density)
            setPadding((Dimens.spacingMd * density).toInt(), (10 * density).toInt(), (Dimens.spacingMd * density).toInt(), (10 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // Description
        val descTv = TextView(ctx).apply {
            text = item.description ?: ""
            textSize = StoreTokens.FontSize.Body.toFloat()
            setTextColor(StoreColors.OnSurface)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(descTv)

        // Quantity
        val qtyTv = TextView(ctx).apply {
            text = "${item.quantity}x"
            textSize = StoreTokens.FontSize.Body.toFloat()
            setTextColor(StoreColors.OnSurfaceVariant)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((50 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        row.addView(qtyTv)

        // Value
        val valueTv = TextView(ctx).apply {
            text = "\$${ViewUtils.formatPrice(item.value)}"
            textSize = StoreTokens.FontSize.Body.toFloat()
            setTypeface(null, Typeface.BOLD)
            setTextColor(StoreColors.PriceColor)
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams((90 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        row.addView(valueTv)

        return row
    }
}
