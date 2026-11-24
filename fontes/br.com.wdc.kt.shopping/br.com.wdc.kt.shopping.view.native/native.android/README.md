# native.android

View implementation using **programmatic Android Views** — no XML layouts, no Compose, no ViewBinding.

## Architecture

### Base Class: `AbstractViewAndroid<P>`

Every view extends `AbstractViewAndroid<P>`, which implements the framework's `CubeView` interface:

```kotlin
class ProductsPanelViewAndroid(presenter: ProductsPanelPresenter) 
    : AbstractViewAndroid<ProductsPanelPresenter>("products-panel", presenter) {

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
2. `doUpdate()` — called by the scheduler when the presenter's scope changes
3. Guards (`lastX != newX`) avoid unnecessary recomputation

### DSL: `AndroidDom`

Kotlin DSL that replaces XML/Compose with programmatic calls:

```kotlin
AndroidDom.build(ctx) {
    vStack(spacing = 8.dp) {
        hStack { 
            label { text = "Title"; textSize = 17f }
            spacer()
        }
        scrollView {
            flowLayout(minChildWidth = 160.dp) {
                // product cards
            }
        }
    }
}
```

Available components: `vStack`, `hStack`, `frame`, `scrollView`, `flowLayout`, `spacer`, `label`/`textView`, `button`, `imageView`, `editText`.

### View Updates: `ViewUpdateScheduler`

The scheduler works via `Handler(Looper.getMainLooper())`:

1. The presenter calls `update()` → marks the view as dirty
2. On the next VSYNC, the scheduler flushes all dirty views
3. Before the flush, `app.commitComputedState()` ensures state consistency
4. Each view runs `doUpdate()` only once per frame

### Implementation Patterns

| Pattern | Description |
|--------|-----------|
| **ListSlot** | View recycling in lists — grows, shrinks and updates in place |
| **FlowLayout** | Custom ViewGroup that lays out children in a responsive grid based on the available width |
| **StateListDrawable** | Visual touch feedback (the background changes color in the `pressed` state) |
| **Guards** | `lastX` variables that avoid reprocessing when the state has not changed |
| **Density-aware** | All dimensions use `* density` for density independence |

### Navigation and Slots

Parent views declare a `ViewSlot` (a `FrameLayout`) that receives child views through navigation:

```kotlin
// In HomeViewAndroid
val contentSlot = newViewSlot(contentContainer)

// When the presenter navigates to "product-details"
// the slot swaps the visible view automatically
```

## Building and running

```bash
cd fontes

# Build the debug APK
./gradlew :view-native-android:assembleDebug

# Deploy to the emulator
cd br.com.wdc.kt.shopping/br.com.wdc.kt.shopping.view.native/native.android
./deploy.sh emulator --emulator-name Pixel_10
```

## File Structure

```
src/androidMain/kotlin/br/com/wdc/shopping/nativeui/android/
├── theme/
│   ├── Colors.kt          # Material Design palette
│   ├── Dimens.kt          # Default dimensions
│   └── Styles.kt          # Style helpers (cards, buttons)
├── toolkit/
│   ├── AbstractViewAndroid.kt   # Base class + ListSlot
│   ├── AndroidDom.kt           # DSL for building views
│   ├── FlowLayout.kt           # Responsive grid ViewGroup
│   ├── ViewUpdateScheduler.kt  # Update coalescing
│   └── ViewUtils.kt            # Formatting, image loading
└── views/
    ├── RootViewAndroid.kt       # Entry point + factory registration
    ├── LoginViewAndroid.kt      # Login screen
    ├── HomeViewAndroid.kt       # Responsive layout with panels/tabs
    ├── ProductsPanelViewAndroid.kt  # Product grid
    ├── PurchasesPanelViewAndroid.kt # Paginated purchase list
    ├── ProductViewAndroid.kt    # Product details
    ├── CartViewAndroid.kt       # Storefront cart
    └── ReceiptViewAndroid.kt    # Purchase receipt
```
