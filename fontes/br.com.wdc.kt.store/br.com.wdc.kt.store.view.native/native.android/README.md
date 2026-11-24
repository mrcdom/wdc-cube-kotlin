# native.android

View implementation using **programmatic Android Views** — no XML layouts, no Compose, no ViewBinding.

## Architecture

### Base Class: `AbstractViewAndroid<S>`

Every view extends `AbstractViewAndroid<S>`, which implements the framework's `CubeView` interface. A view receives
the application and the scope it paints — never the presenter — and calls the scope's actions (`scope.onX(...)`)
inside `safeAction`:

```kotlin
class ProductsPanelViewAndroid(app: RootPresenter, scope: ProductsPanelScope)
    : AbstractViewAndroid<ProductsPanelScope>("products-panel-view", app, scope) {

    override fun createView(): View = AndroidDom.build(ctx) {
        // DSL for building the UI programmatically
    }

    override fun doUpdate() {
        // Efficient update with guards (comparison with the previous state)
    }
}
```

The lifecycle is:
1. `createView()` — builds the view tree programmatically via `AndroidDom`
2. `doUpdate()` — called once at creation and then by the scheduler whenever the scope asks for a repaint
3. Guards (`lastX != newX`) avoid unnecessary recomputation

### DSL: `AndroidDom`

Kotlin DSL that replaces XML/Compose with programmatic calls:

```kotlin
AndroidDom.build(ctx) {
    vStack(spacing = dp(8)) {
        hStack {
            textView { text = "Title"; textSize = 17f }
            flexSpacer()
        }
        scrollView {
            flowLayout(minChildWidth = dp(160)) {
                // product cards
            }
        }
    }
}
```

Available components: `vStack`, `hStack`, `frame`, `scrollView`, `flowLayout`, `spacer`, `flexSpacer`, `textView`, `button`, `imageView`, `editText`, `progressBar`.

### View Updates: `ViewUpdateScheduler`

The scheduler works via `Handler(Looper.getMainLooper())`:

1. A scope change calls the view's `update()` → marks the view as dirty
2. On the next main-looper pass, the scheduler flushes
3. Each flush first runs `app.commitComputedState()` once (views it marks dirty join the same flush)
4. Then each dirty view runs `doUpdate()` once

### Implementation Patterns

| Pattern | Description |
|--------|-----------|
| **ListSlot** | View recycling in lists — grows, shrinks and updates in place |
| **FlowLayout** | Custom ViewGroup that lays out children in a responsive grid based on the available width |
| **StateListDrawable** | Visual touch feedback (the background changes color in the `pressed` state) |
| **Guards** | `lastX` variables that avoid reprocessing when the state has not changed |
| **Density-aware** | All dimensions use `* density` for density independence |

### Navigation and Slots

Parent views declare a `ViewSlot` over a `FrameLayout` (`newViewSlot(container)`). The parent scope holds the child's scope (`content`, and
`productsPanel`/`purchasesPanel` in Home); the parent asks the layer's registry (`ScopeViews`, created in `MainActivity` and
passed to the views that have slots) for that scope's view, which is created once per scope, reused, and released
with the scope:

```kotlin
// In HomeViewAndroid
detailSlot = newViewSlot(detailContainer)

// In doUpdate(): the slot swaps the visible view when the child scope changes
detailSlot.sync(scope.content?.let(views::viewOf))
```

A child view repaints only when its own scope changes, never along with its parent.

The registry (`ScopeViews`, scope class → view) is filled in `MainActivity` before the first navigation; the root view
is `views.viewOf(app.scope)`.

## Building and running

```bash
cd fontes

# Build the debug APK
./gradlew :view-native-android:assembleDebug

# Deploy to the emulator
cd br.com.wdc.kt.store/br.com.wdc.kt.store.view.native/native.android
./deploy.sh emulator --emulator-name Pixel_10
```

## File Structure

```
src/main/kotlin/br/com/wdc/store/nativeui/android/
├── MainActivity.kt        # Entry point: bootstrap and the view catalog (ScopeViews)
├── AndroidNativeRootPresenter.kt
├── AndroidScheduledExecutor.kt
├── theme/
│   ├── Colors.kt          # Material Design palette
│   ├── Dimens.kt          # Default dimensions
│   ├── Icons.kt           # Icons
│   └── Styles.kt          # Style helpers (cards, buttons)
├── toolkit/
│   ├── AbstractViewAndroid.kt   # Base class + ViewSlot and ListSlot
│   ├── AndroidDom.kt           # DSL for building views
│   ├── FlowLayout.kt           # Responsive grid ViewGroup
│   ├── ViewUpdateScheduler.kt  # Update coalescing
│   └── ViewUtils.kt            # Formatting, image loading
└── views/
    ├── RootViewAndroid.kt       # Page frame: content slot and error toast
    ├── LoginViewAndroid.kt      # Login screen
    ├── HomeViewAndroid.kt       # Responsive layout with panels/tabs
    ├── ProductsPanelViewAndroid.kt  # Product grid
    ├── PurchasesPanelViewAndroid.kt # Paginated purchase list
    ├── ProductViewAndroid.kt    # Product details
    ├── CartViewAndroid.kt       # WDC Store cart: hosts the view of each row
    ├── CartItemViewAndroid.kt   # A cart row: quantity controls and remove
    └── ReceiptViewAndroid.kt    # Purchase receipt
```
