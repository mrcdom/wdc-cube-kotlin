package br.com.wdc.store.nativeui.android.views

import br.com.wdc.store.i18n.commonAppName
import br.com.wdc.store.i18n.homeCart
import br.com.wdc.store.i18n.homeGreeting
import br.com.wdc.store.i18n.homeSignOut
import br.com.wdc.store.i18n.homeTabProducts
import br.com.wdc.store.i18n.homeTabPurchases
import br.com.wdc.store.design.StoreTokens
import android.graphics.Color
import android.graphics.Typeface
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import br.com.wdc.store.nativeui.android.theme.*
import br.com.wdc.store.nativeui.android.toolkit.AbstractViewAndroid
import br.com.wdc.store.nativeui.android.toolkit.AndroidDom
import br.com.wdc.store.nativeui.android.AndroidNativeRootPresenter
import br.com.wdc.store.presentation.presenter.restricted.home.HomeScope

class HomeViewAndroid(app: AndroidNativeRootPresenter, scope: HomeScope) : AbstractViewAndroid<HomeScope>("home-view", app, scope) {

    // Compact header
    private lateinit var compactHeader: LinearLayout
    private lateinit var nicknameLabel: TextView
    private lateinit var cartButton: Button
    private lateinit var cartBadgeLabel: TextView

    // Wide header
    private lateinit var wideHeader: LinearLayout
    private lateinit var wideNicknameLabel: TextView
    private lateinit var wideCartButton: Button
    private lateinit var wideCartBadgeLabel: TextView

    // Tabs (compact)
    private lateinit var tabsContainer: LinearLayout
    private lateinit var productsTab: Button
    private lateinit var purchasesTab: Button
    private lateinit var productsTabLine: View
    private lateinit var purchasesTabLine: View

    // Content
    private lateinit var compactContentContainer: LinearLayout
    private lateinit var compactPanelsContainer: FrameLayout
    private lateinit var widePanelsContainer: LinearLayout
    private lateinit var detailContainer: FrameLayout
    private lateinit var errorLabel: TextView
    private lateinit var detailSlot: ViewSlot

    private var mountedProductsPanel: AbstractViewAndroid<*>? = null
    private var mountedPurchasesPanel: AbstractViewAndroid<*>? = null
    private var selectedTab = 0
    private var lastTab = -1
    private var lastNick: String? = null
    private var lastCartCount = -1
    private var isWide = false

    override fun createView(): View {
        val ctx = RootViewAndroid.appContext
        val density = ctx.resources.displayMetrics.density

        return AndroidDom.build(ctx) {
            val root = parent()
            root.setBackgroundColor(StoreColors.Background)

            vStack(configure = {
                (layoutParams as FrameLayout.LayoutParams).apply {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                }
            }) {
                // === COMPACT HEADER (2 rows) ===
                compactHeader = vStack(configure = {
                    setBackgroundColor(StoreColors.Primary)
                    clipChildren = false
                    clipToPadding = false
                    (layoutParams as LinearLayout.LayoutParams).height = (Dimens.headerHeightCompact * density).toInt()
                    setPadding((Dimens.spacingMd * density).toInt(), (10 * density).toInt(), (Dimens.spacingMd * density).toInt(), (10 * density).toInt())
                }) {
                    // Row 1: Logo + "WDC Store" + Logout icon (all on left)
                    hStack(configure = {
                        gravity = Gravity.CENTER_VERTICAL
                        (layoutParams as LinearLayout.LayoutParams).apply {
                            height = (32 * density).toInt()
                        }
                    }) {
                        // Logo avatar
                        frame(configure = {
                            val bg = StoreStyles.roundedBackground(StoreColors.WhiteOverlay20, Dimens.radiusSm * density)
                            background = bg
                            (layoutParams as LinearLayout.LayoutParams).apply {
                                width = (32 * density).toInt()
                                height = (32 * density).toInt()
                            }
                        }) {
                            imageView {
                                setImageDrawable(StoreIcons.localMall(ctx, 20, Color.WHITE))
                                (layoutParams as FrameLayout.LayoutParams).apply {
                                    width = (20 * density).toInt()
                                    height = (20 * density).toInt()
                                    gravity = Gravity.CENTER
                                }
                            }
                        }
                        frame(configure = {
                            (layoutParams as LinearLayout.LayoutParams).apply {
                                marginStart = (Dimens.spacingSm * density).toInt()
                                width = ViewGroup.LayoutParams.WRAP_CONTENT
                                height = ViewGroup.LayoutParams.WRAP_CONTENT
                            }
                        }) {
                            textView {
                                text = messages.commonAppName()
                                textSize = StoreTokens.FontSize.Title.toFloat()
                                setTypeface(null, Typeface.BOLD)
                                setTextColor(Color.WHITE)
                            }
                            textView {
                                text = "native"
                                textSize = 9f
                                setTextColor(Color.WHITE)
                                alpha = 0.45f
                                (layoutParams as FrameLayout.LayoutParams).apply {
                                    width = ViewGroup.LayoutParams.WRAP_CONTENT
                                    gravity = Gravity.BOTTOM or Gravity.END
                                    topMargin = (Dimens.spacingXl * density).toInt()
                                }
                            }
                        }
                        // Logout icon (subtle, near brand)
                        frame(configure = {
                            val bg = StoreStyles.roundedBackground(StoreColors.WhiteOverlay10, 14 * density)
                            background = bg
                            (layoutParams as LinearLayout.LayoutParams).apply {
                                width = (28 * density).toInt()
                                height = (28 * density).toInt()
                                marginStart = (Dimens.spacingMd * density).toInt()
                            }
                            setOnClickListener { safeAction("exit") { scope.onExit() } }
                            contentDescription = messages.homeSignOut()
                        }) {
                            imageView {
                                setImageDrawable(StoreIcons.logout(ctx, 16, Color.WHITE))
                                alpha = 0.7f
                                (layoutParams as FrameLayout.LayoutParams).apply {
                                    width = (16 * density).toInt()
                                    height = (16 * density).toInt()
                                    gravity = Gravity.CENTER
                                }
                            }
                        }
                    }

                    spacer(height = (8 * density).toInt())

                    // Row 2: Nickname + Cart button
                    hStack(configure = {
                        gravity = Gravity.CENTER_VERTICAL
                        clipChildren = false
                        clipToPadding = false
                        (layoutParams as LinearLayout.LayoutParams).apply {
                            height = (32 * density).toInt()
                        }
                    }) {
                        nicknameLabel = textView {
                            textSize = StoreTokens.FontSize.Body.toFloat()
                            setTextColor(StoreColors.WhiteOverlay85)
                            (layoutParams as LinearLayout.LayoutParams).apply {
                                weight = 1f
                                width = 0
                            }
                        }
                        // Cart button with badge
                        frame(configure = {
                            clipChildren = false
                            clipToPadding = false
                            (layoutParams as LinearLayout.LayoutParams).apply {
                                width = ViewGroup.LayoutParams.WRAP_CONTENT
                                height = ViewGroup.LayoutParams.WRAP_CONTENT
                            }
                        }) {
                            cartButton = button(messages.homeCart()) {
                                textSize = 13f
                                setTextColor(Color.WHITE)
                                isAllCaps = false
                                stateListAnimator = null
                                minimumHeight = 0
                                minimumWidth = 0
                                background = StoreStyles.roundedBackground(StoreColors.WhiteOverlay20, 10 * density)
                                val padH = (Dimens.spacingMd * density).toInt()
                                val padV = (Dimens.spacingXs * density).toInt()
                                setPadding(padH, padV, padH, padV)
                                setCompoundDrawablesRelativeWithIntrinsicBounds(
                                    StoreIcons.shoppingCart(ctx, 16, Color.WHITE), null, null, null
                                )
                                compoundDrawablePadding = (Dimens.spacingXs * density).toInt()
                                (layoutParams as FrameLayout.LayoutParams).apply {
                                    width = ViewGroup.LayoutParams.WRAP_CONTENT
                                    height = (30 * density).toInt()
                                }
                                setOnClickListener { safeAction("cart") { scope.onOpenCart() } }
                            }
                            cartBadgeLabel = textView {
                                textSize = 11f
                                setTypeface(null, Typeface.BOLD)
                                setTextColor(Color.WHITE)
                                setBackgroundColor(StoreColors.Error)
                                gravity = Gravity.CENTER
                                visibility = View.GONE
                                val bg = StoreStyles.roundedBackground(StoreColors.Error, 9 * density)
                                background = bg
                                (layoutParams as FrameLayout.LayoutParams).apply {
                                    width = (18 * density).toInt()
                                    height = (18 * density).toInt()
                                    gravity = Gravity.TOP or Gravity.END
                                    topMargin = (-4 * density).toInt()
                                    marginEnd = (-4 * density).toInt()
                                }
                            }
                        }
                    }
                }

                // === WIDE HEADER (single row) ===
                wideHeader = hStack(configure = {
                    setBackgroundColor(StoreColors.Primary)
                    visibility = View.GONE
                    clipChildren = false
                    clipToPadding = false
                    gravity = Gravity.CENTER_VERTICAL
                    (layoutParams as LinearLayout.LayoutParams).height = (64 * density).toInt()
                    setPadding((Dimens.spacingXl * density).toInt(), (Dimens.spacingMd * density).toInt(), (Dimens.spacingXl * density).toInt(), (Dimens.spacingMd * density).toInt())
                }) {
                    // Logo
                    frame(configure = {
                        val bg = StoreStyles.roundedBackground(StoreColors.WhiteOverlay20, 10 * density)
                        background = bg
                        (layoutParams as LinearLayout.LayoutParams).apply {
                            width = (36 * density).toInt()
                            height = (36 * density).toInt()
                        }
                    }) {
                        imageView {
                            setImageDrawable(StoreIcons.localMall(ctx, 22, Color.WHITE))
                            (layoutParams as FrameLayout.LayoutParams).apply {
                                width = (22 * density).toInt()
                                height = (22 * density).toInt()
                                gravity = Gravity.CENTER
                            }
                        }
                    }
                    frame(configure = {
                        (layoutParams as LinearLayout.LayoutParams).apply {
                            marginStart = (Dimens.spacingMd * density).toInt()
                            width = ViewGroup.LayoutParams.WRAP_CONTENT
                            height = ViewGroup.LayoutParams.WRAP_CONTENT
                        }
                    }) {
                        textView {
                            text = messages.commonAppName()
                            textSize = 18f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.WHITE)
                        }
                        textView {
                            text = "native"
                            textSize = 9f
                            setTextColor(Color.WHITE)
                            alpha = 0.45f
                            (layoutParams as FrameLayout.LayoutParams).apply {
                                width = ViewGroup.LayoutParams.WRAP_CONTENT
                                gravity = Gravity.BOTTOM or Gravity.END
                                topMargin = (22 * density).toInt()
                            }
                        }
                    }

                    // Logout icon button (subtle, separated from actions)
                    frame(configure = {
                        val bg = StoreStyles.roundedBackground(StoreColors.WhiteOverlay10, 16 * density)
                        background = bg
                        (layoutParams as LinearLayout.LayoutParams).apply {
                            width = (32 * density).toInt()
                            height = (32 * density).toInt()
                            marginStart = (Dimens.spacingLg * density).toInt()
                        }
                        setOnClickListener { safeAction("exit") { scope.onExit() } }
                        contentDescription = messages.homeSignOut()
                    }) {
                        imageView {
                            setImageDrawable(StoreIcons.logout(ctx, 18, Color.WHITE))
                            alpha = 0.7f
                            (layoutParams as FrameLayout.LayoutParams).apply {
                                width = (18 * density).toInt()
                                height = (18 * density).toInt()
                                gravity = Gravity.CENTER
                            }
                        }
                    }

                    // Spacer (pushes right elements to the end)
                    textView {
                        (layoutParams as LinearLayout.LayoutParams).apply {
                            width = 0
                            height = 0
                            weight = 1f
                        }
                    }

                    // Greeting pill
                    frame(configure = {
                        val bg = StoreStyles.roundedBackground(StoreColors.WhiteOverlay15, Dimens.radiusXl * density)
                        background = bg
                        val padH = (Dimens.spacingLg * density).toInt()
                        val padV = (Dimens.spacingSm * density).toInt()
                        setPadding(padH, padV, padH, padV)
                        (layoutParams as LinearLayout.LayoutParams).apply {
                            width = ViewGroup.LayoutParams.WRAP_CONTENT
                            height = ViewGroup.LayoutParams.WRAP_CONTENT
                            marginEnd = (Dimens.spacingLg * density).toInt()
                        }
                    }) {
                        wideNicknameLabel = textView {
                            textSize = StoreTokens.FontSize.Body.toFloat()
                            setTextColor(Color.WHITE)
                            (layoutParams as FrameLayout.LayoutParams).width = ViewGroup.LayoutParams.WRAP_CONTENT
                        }
                    }

                    // Wide cart button
                    frame(configure = {
                        clipChildren = false
                        clipToPadding = false
                        (layoutParams as LinearLayout.LayoutParams).apply {
                            width = ViewGroup.LayoutParams.WRAP_CONTENT
                            height = ViewGroup.LayoutParams.WRAP_CONTENT
                        }
                    }) {
                        wideCartButton = button(messages.homeCart()) {
                            textSize = StoreTokens.FontSize.Body.toFloat()
                            setTextColor(Color.WHITE)
                            isAllCaps = false
                            stateListAnimator = null
                            minimumHeight = 0
                            minimumWidth = 0
                            background = StoreStyles.roundedBackground(StoreColors.WhiteOverlay20, Dimens.radiusMd * density)
                            val padH = (14 * density).toInt()
                            val padV = (Dimens.spacingSm * density).toInt()
                            setPadding(padH, padV, padH, padV)
                            setCompoundDrawablesRelativeWithIntrinsicBounds(
                                StoreIcons.shoppingCart(ctx, 18, Color.WHITE), null, null, null
                            )
                            compoundDrawablePadding = (6 * density).toInt()
                            (layoutParams as FrameLayout.LayoutParams).apply {
                                width = ViewGroup.LayoutParams.WRAP_CONTENT
                                height = ViewGroup.LayoutParams.WRAP_CONTENT
                            }
                            setOnClickListener { safeAction("cart") { scope.onOpenCart() } }
                        }
                        wideCartBadgeLabel = textView {
                            textSize = 11f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.WHITE)
                            gravity = Gravity.CENTER
                            visibility = View.GONE
                            background = StoreStyles.roundedBackground(StoreColors.Error, 9 * density)
                            (layoutParams as FrameLayout.LayoutParams).apply {
                                width = (18 * density).toInt()
                                height = (18 * density).toInt()
                                gravity = Gravity.TOP or Gravity.END
                            }
                        }
                    }
                }

                // === CONTENT AREA (includes tabs + panels + detail overlay) ===
                frame(configure = {
                    (layoutParams as LinearLayout.LayoutParams).apply {
                        width = ViewGroup.LayoutParams.MATCH_PARENT
                        height = 0
                        weight = 1f
                    }
                }) {
                    // Compact: tabs + panel in a vStack
                    compactContentContainer = vStack(configure = {
                        (layoutParams as FrameLayout.LayoutParams).apply {
                            width = ViewGroup.LayoutParams.MATCH_PARENT
                            height = ViewGroup.LayoutParams.MATCH_PARENT
                        }
                    }) {
                        // === TABS (compact only) ===
                        tabsContainer = hStack(configure = {
                            setBackgroundColor(StoreColors.Background)
                            (layoutParams as LinearLayout.LayoutParams).height = (Dimens.tabsHeight * density).toInt()
                            gravity = Gravity.BOTTOM
                            setPadding((Dimens.spacingLg * density).toInt(), 0, (Dimens.spacingLg * density).toInt(), 0)
                        }) {
                            // Products tab with underline
                            vStack(configure = {
                                gravity = Gravity.CENTER_HORIZONTAL
                                (layoutParams as LinearLayout.LayoutParams).apply {
                                    width = ViewGroup.LayoutParams.WRAP_CONTENT
                                    height = ViewGroup.LayoutParams.MATCH_PARENT
                                    marginEnd = (Dimens.spacingXl * density).toInt()
                                }
                            }) {
                                productsTab = button(messages.homeTabProducts().uppercase()) {
                                    textSize = StoreTokens.FontSize.Body.toFloat()
                                    setTypeface(null, Typeface.BOLD)
                                    setTextColor(StoreColors.Primary)
                                    setBackgroundColor(Color.TRANSPARENT)
                                    setCompoundDrawablesRelativeWithIntrinsicBounds(
                                        StoreIcons.shoppingBag(ctx, 22, StoreColors.Primary), null, null, null
                                    )
                                    compoundDrawablePadding = (6 * density).toInt()
                                    (layoutParams as LinearLayout.LayoutParams).apply {
                                        width = ViewGroup.LayoutParams.WRAP_CONTENT
                                        height = 0
                                        weight = 1f
                                    }
                                    setOnClickListener { switchToProducts() }
                                }
                                productsTabLine = frame(configure = {
                                    setBackgroundColor(StoreColors.Primary)
                                    (layoutParams as LinearLayout.LayoutParams).apply {
                                        width = ViewGroup.LayoutParams.MATCH_PARENT
                                        height = (3 * density).toInt()
                                    }
                                })
                            }
                            // Purchases tab with underline
                            vStack(configure = {
                                gravity = Gravity.CENTER_HORIZONTAL
                                (layoutParams as LinearLayout.LayoutParams).apply {
                                    width = ViewGroup.LayoutParams.WRAP_CONTENT
                                    height = ViewGroup.LayoutParams.MATCH_PARENT
                                }
                            }) {
                                purchasesTab = button(messages.homeTabPurchases().uppercase()) {
                                    textSize = StoreTokens.FontSize.Body.toFloat()
                                    setTextColor(StoreColors.OnSurfaceVariant)
                                    setBackgroundColor(Color.TRANSPARENT)
                                    setCompoundDrawablesRelativeWithIntrinsicBounds(
                                        StoreIcons.inventory(ctx, 22, StoreColors.OnSurfaceVariant), null, null, null
                                    )
                                    compoundDrawablePadding = (6 * density).toInt()
                                    (layoutParams as LinearLayout.LayoutParams).apply {
                                        width = ViewGroup.LayoutParams.WRAP_CONTENT
                                        height = 0
                                        weight = 1f
                                    }
                                    setOnClickListener { switchToPurchases() }
                                }
                                purchasesTabLine = frame(configure = {
                                    setBackgroundColor(StoreColors.Primary)
                                    visibility = View.INVISIBLE
                                    (layoutParams as LinearLayout.LayoutParams).apply {
                                        width = ViewGroup.LayoutParams.MATCH_PARENT
                                        height = (3 * density).toInt()
                                    }
                                })
                            }
                        }

                        // Compact panels container
                        compactPanelsContainer = frame(configure = {
                            (layoutParams as LinearLayout.LayoutParams).apply {
                                width = ViewGroup.LayoutParams.MATCH_PARENT
                                height = 0
                                weight = 1f
                            }
                            val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                                override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                                    val dx = e2.x - (e1?.x ?: e2.x)
                                    if (kotlin.math.abs(dx) > kotlin.math.abs(e2.y - (e1?.y ?: e2.y)) && kotlin.math.abs(dx) > 100) {
                                        if (dx < 0) switchToPurchases() else switchToProducts()
                                        return true
                                    }
                                    return false
                                }
                            })
                            setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }
                        })
                    }

                    // Wide panels (side by side)
                    widePanelsContainer = hStack(spacing = (Dimens.spacingLg * density).toInt(), configure = {
                        visibility = View.GONE
                        setPadding((Dimens.spacingLg * density).toInt(), (Dimens.spacingLg * density).toInt(), (Dimens.spacingLg * density).toInt(), (Dimens.spacingLg * density).toInt())
                        (layoutParams as FrameLayout.LayoutParams).apply {
                            width = ViewGroup.LayoutParams.MATCH_PARENT
                            height = ViewGroup.LayoutParams.MATCH_PARENT
                            gravity = Gravity.CENTER_HORIZONTAL
                        }
                    }) {
                        // Products panel (60%)
                        frame(configure = {
                            StoreStyles.applyCardStyle(this, Dimens.radiusSm.toFloat())
                            (layoutParams as LinearLayout.LayoutParams).apply {
                                width = 0
                                height = ViewGroup.LayoutParams.MATCH_PARENT
                                weight = 3f
                            }
                        })

                        // Purchases panel (40%)
                        frame(configure = {
                            StoreStyles.applyCardStyle(this, Dimens.radiusSm.toFloat())
                            (layoutParams as LinearLayout.LayoutParams).apply {
                                width = 0
                                height = ViewGroup.LayoutParams.MATCH_PARENT
                                weight = 2f
                            }
                        })
                    }

                    // Detail container (overlay)
                    detailContainer = frame(configure = {
                        setBackgroundColor(StoreColors.Background)
                        visibility = View.GONE
                        (layoutParams as FrameLayout.LayoutParams).apply {
                            width = ViewGroup.LayoutParams.MATCH_PARENT
                            height = ViewGroup.LayoutParams.MATCH_PARENT
                            gravity = Gravity.CENTER_HORIZONTAL
                        }
                    })
                }
            }

            // Error label overlaid
            errorLabel = textView {
                setTextColor(StoreColors.Error)
                textSize = StoreTokens.FontSize.Body.toFloat()
                gravity = Gravity.CENTER
                visibility = View.GONE
                (layoutParams as FrameLayout.LayoutParams).apply {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    topMargin = (Dimens.headerHeightCompact * density + Dimens.spacingSm * density).toInt()
                    marginStart = (Dimens.spacingLg * density).toInt()
                    marginEnd = (Dimens.spacingLg * density).toInt()
                }
            }

            detailSlot = newViewSlot(detailContainer)

            // Responsive breakpoint detection
            root.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
                val widthDp = (right - left) / density
                val nowWide = widthDp >= Dimens.wideBreakpointDp
                if (nowWide != isWide) {
                    isWide = nowWide
                    applyLayout()
                }
                // Apply max content width in wide mode
                if (nowWide) {
                    val availablePx = right - left

                    val maxHomePx = (Dimens.maxContentWidthDp * density).toInt()
                    val cappedHome = if (availablePx > maxHomePx) maxHomePx else ViewGroup.LayoutParams.MATCH_PARENT
                    (widePanelsContainer.layoutParams as FrameLayout.LayoutParams).width = cappedHome
                    widePanelsContainer.requestLayout()

                    val maxDetailPx = (Dimens.maxDetailWidthDp * density).toInt()
                    val cappedDetail = if (availablePx > maxDetailPx) maxDetailPx else ViewGroup.LayoutParams.MATCH_PARENT
                    (detailContainer.layoutParams as FrameLayout.LayoutParams).width = cappedDetail
                    detailContainer.requestLayout()
                }
            }
        }
    }

    private fun applyLayout() {
        val density = rootView.resources.displayMetrics.density
        if (isWide) {
            compactHeader.visibility = View.GONE
            wideHeader.visibility = View.VISIBLE
            compactContentContainer.visibility = View.GONE
            widePanelsContainer.visibility = View.VISIBLE
            remountPanelsForWide()
        } else {
            compactHeader.visibility = View.VISIBLE
            wideHeader.visibility = View.GONE
            compactContentContainer.visibility = View.VISIBLE
            widePanelsContainer.visibility = View.GONE
            remountPanelsForCompact()
        }
    }

    private fun remountPanelsForWide() {
        val prodView = (mountedProductsPanel as? AbstractViewAndroid<*>)?.rootView ?: return
        val purchView = (mountedPurchasesPanel as? AbstractViewAndroid<*>)?.rootView ?: return

        (prodView.parent as? ViewGroup)?.removeView(prodView)
        (purchView.parent as? ViewGroup)?.removeView(purchView)

        val wideProds = widePanelsContainer.getChildAt(0) as FrameLayout
        val widePurch = widePanelsContainer.getChildAt(1) as FrameLayout

        wideProds.removeAllViews()
        widePurch.removeAllViews()
        wideProds.addView(prodView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        widePurch.addView(purchView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        prodView.visibility = View.VISIBLE
        purchView.visibility = View.VISIBLE
    }

    private fun remountPanelsForCompact() {
        val prodView = (mountedProductsPanel as? AbstractViewAndroid<*>)?.rootView ?: return
        val purchView = (mountedPurchasesPanel as? AbstractViewAndroid<*>)?.rootView ?: return

        (prodView.parent as? ViewGroup)?.removeView(prodView)
        (purchView.parent as? ViewGroup)?.removeView(purchView)

        compactPanelsContainer.removeAllViews()
        compactPanelsContainer.addView(prodView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        compactPanelsContainer.addView(purchView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        prodView.visibility = if (selectedTab == 0) View.VISIBLE else View.GONE
        purchView.visibility = if (selectedTab == 1) View.VISIBLE else View.GONE
    }

    private fun mountPanel(panel: AbstractViewAndroid<*>, isProducts: Boolean) {
        val childView = (panel as AbstractViewAndroid<*>).rootView
        (childView.parent as? ViewGroup)?.removeView(childView)

        if (isWide) {
            val container = if (isProducts) widePanelsContainer.getChildAt(0) as FrameLayout
            else widePanelsContainer.getChildAt(1) as FrameLayout
            container.removeAllViews()
            container.addView(childView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            childView.visibility = View.VISIBLE
        } else {
            compactPanelsContainer.addView(childView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            childView.visibility = if (isProducts) {
                if (selectedTab == 0) View.VISIBLE else View.GONE
            } else {
                if (selectedTab == 1) View.VISIBLE else View.GONE
            }
        }
    }

    override fun doUpdate() {

        // Nickname
        val nick = scope.nickName
        if (nick != lastNick) {
            lastNick = nick
            val greeting = if (!nick.isNullOrBlank()) messages.homeGreeting(nick) else ""
            nicknameLabel.text = greeting
            wideNicknameLabel.text = greeting
        }

        // Cart badge
        val cartCount = scope.cartItemCount
        if (cartCount != lastCartCount) {
            lastCartCount = cartCount
            val show = cartCount > 0
            val text = cartCount.toString()
            cartBadgeLabel.text = text
            cartBadgeLabel.visibility = if (show) View.VISIBLE else View.GONE
            wideCartBadgeLabel.text = text
            wideCartBadgeLabel.visibility = if (show) View.VISIBLE else View.GONE
        }

        // Mount panels
        val productsPanel = scope.productsPanel?.let(::viewOf)
        val purchasesPanel = scope.purchasesPanel?.let(::viewOf)

        if (productsPanel != null && mountedProductsPanel !== productsPanel) {
            mountedProductsPanel = productsPanel
            mountPanel(productsPanel, isProducts = true)
        }
        if (purchasesPanel != null && mountedPurchasesPanel !== purchasesPanel) {
            mountedPurchasesPanel = purchasesPanel
            mountPanel(purchasesPanel, isProducts = false)
        }

        // Tab visibility (compact)
        if (!isWide && selectedTab != lastTab) {
            lastTab = selectedTab
            (mountedProductsPanel as? AbstractViewAndroid<*>)?.rootView?.visibility = if (selectedTab == 0) View.VISIBLE else View.GONE
            (mountedPurchasesPanel as? AbstractViewAndroid<*>)?.let {
                it.rootView.visibility = if (selectedTab == 1) View.VISIBLE else View.GONE
                if (selectedTab == 1) {
                    it.rootView.post { it.redraw() }
                }
            }
            updateTabAppearance()
        }

        // Detail overlay
        val contentView = scope.content?.let(::viewOf)
        detailSlot.sync(contentView)
        if (contentView != null) {
            detailContainer.visibility = View.VISIBLE
            if (isWide) widePanelsContainer.visibility = View.GONE
        } else {
            detailContainer.visibility = View.GONE
            if (isWide) widePanelsContainer.visibility = View.VISIBLE
        }

        // Error
        val errorMessage = scope.errorMessage
        if (errorMessage != null) {
            errorLabel.text = translate(errorMessage)
            errorLabel.visibility = View.VISIBLE
        } else {
            errorLabel.visibility = View.GONE
        }
    }

    private fun updateTabAppearance() {
        val ctx = RootViewAndroid.appContext
        val prodColor = if (selectedTab == 0) StoreColors.Primary else StoreColors.OnSurfaceVariant
        productsTab.setTextColor(prodColor)
        productsTab.setTypeface(null, if (selectedTab == 0) Typeface.BOLD else Typeface.NORMAL)
        productsTab.setCompoundDrawablesRelativeWithIntrinsicBounds(
            StoreIcons.shoppingBag(ctx, 22, prodColor), null, null, null
        )
        productsTabLine.visibility = if (selectedTab == 0) View.VISIBLE else View.INVISIBLE

        val purchColor = if (selectedTab == 1) StoreColors.Primary else StoreColors.OnSurfaceVariant
        purchasesTab.setTextColor(purchColor)
        purchasesTab.setTypeface(null, if (selectedTab == 1) Typeface.BOLD else Typeface.NORMAL)
        purchasesTab.setCompoundDrawablesRelativeWithIntrinsicBounds(
            StoreIcons.inventory(ctx, 22, purchColor), null, null, null
        )
        purchasesTabLine.visibility = if (selectedTab == 1) View.VISIBLE else View.INVISIBLE
    }

    private fun switchToProducts() {
        if (selectedTab != 0) {
            selectedTab = 0
            lastTab = 0
            (mountedProductsPanel as? AbstractViewAndroid<*>)?.rootView?.visibility = View.VISIBLE
            (mountedPurchasesPanel as? AbstractViewAndroid<*>)?.rootView?.visibility = View.GONE
            updateTabAppearance()
        }
    }

    private fun switchToPurchases() {
        if (selectedTab != 1) {
            selectedTab = 1
            lastTab = 1
            (mountedProductsPanel as? AbstractViewAndroid<*>)?.rootView?.visibility = View.GONE
            (mountedPurchasesPanel as? AbstractViewAndroid<*>)?.let {
                it.rootView.visibility = View.VISIBLE
                it.redraw()
            }
            updateTabAppearance()
        }
    }
}
