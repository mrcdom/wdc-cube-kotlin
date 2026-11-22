# native.ios

View implementation using **UIKit** via Kotlin/Native — no SwiftUI, no Storyboards, purely programmatic.

## Architecture

### Base Class: `AbstractViewIos<P>`

Every view extends `AbstractViewIos<P>`, which implements the framework's `CubeView` interface:

```kotlin
class ProductsPanelViewIos(presenter: ProductsPanelPresenter) 
    : AbstractViewIos<ProductsPanelPresenter>("products-panel", presenter) {

    override fun createView(): UIView = UIKitDom.build {
        // DSL for building UIViews with Auto Layout
    }

    override fun doUpdate() {
        // Efficient update with guards
    }
}
```

The lifecycle is:
1. `createView()` — builds the `UIView` hierarchy programmatically via `UIKitDom`
2. `doUpdate()` — called by the scheduler when the presenter signals a state change
3. Guards (`lastX != newX`) avoid unnecessary UIKit manipulation

### DSL: `UIKitDom`

Kotlin DSL that builds UIViews with Auto Layout via `NSLayoutConstraint`:

```kotlin
UIKitDom.build {
    val root = parent()
    
    vStack(spacing = 8.0) {
        label { 
            text = "Title"
            font = UIFont.boldSystemFontOfSize(17.0) 
        }
    }
    
    scrollView(configure = {
        NSLayoutConstraint.activateConstraints(listOf(
            topAnchor.constraintEqualToAnchor(root.topAnchor),
            leadingAnchor.constraintEqualToAnchor(root.leadingAnchor),
            trailingAnchor.constraintEqualToAnchor(root.trailingAnchor),
            bottomAnchor.constraintEqualToAnchor(root.bottomAnchor)
        ))
    }) {
        flowLayout(minChildWidth = 160.0) { }
    }
}
```

Available components: `vStack`, `hStack`, `view`, `scrollView`, `flowLayout`, `label`, `button`, `textField`, `imageView`.

All views use `translatesAutoresizingMaskIntoConstraints = false` (set automatically by the DSL) and are positioned with Auto Layout constraints.

### View Updates: `ViewUpdateScheduler`

The scheduler works via `dispatch_async(dispatch_get_main_queue())`:

1. The presenter calls `update()` → marks the view as dirty
2. On the next run loop iteration, the scheduler flushes
3. Before the flush, `app.commitComputedState()` ensures consistency
4. Each view runs `doUpdate()` only once per cycle

### Implementation Patterns

| Pattern | Description |
|--------|-----------|
| **ListSlot** | View recycling in lists — supports `UIStackView` and generic `UIView` |
| **FlowLayoutView** | Custom constraint-based view that lays out children in a responsive grid |
| **retainForGC()** | Keeps the Kotlin/Native GC from collecting gesture targets (UIKit uses weak refs) |
| **UILongPressGestureRecognizer** | Visual touch feedback (minimumPressDuration=0 to detect touch-down) |
| **pin()** | Helper that anchors a view to the parent's 4 edges via constraints |
| **center()** | Helper that centers a view in the parent |
| **Guards** | `lastX` variables that avoid redrawing when the state has not changed |

### Rotation Detection and Responsive Layout

`HomeViewIos` observes `UIDeviceOrientationDidChangeNotification` to re-detect the layout when the device rotates:

```kotlin
UIDevice.currentDevice.beginGeneratingDeviceOrientationNotifications()
orientationObserver = NSNotificationCenter.defaultCenter.addObserverForName(
    name = UIDeviceOrientationDidChangeNotification,
    `object` = null, queue = null
) { _ ->
    dispatch_async(dispatch_get_main_queue()) {
        this@HomeViewIos.detectLayout()
    }
}
```

Breakpoints:
- `WIDE_BREAKPOINT = 700pt` — above this, side-by-side layout (products + purchases)
- `MAX_CONTENT_WIDTH = 1200pt` — maximum width of the main container
- `MAX_DETAIL_WIDTH = 560pt` — maximum width of the detail screens

### Navigation and Slots

Parent views declare a `ViewSlot` (a `UIView`) that receives child views through navigation. The slot pins the child view with constraints on all 4 edges:

```kotlin
// In HomeViewIos
val detailSlot = newViewSlot(detailContainer)

// When the presenter navigates to "product-details"
// the slot swaps the visible view automatically
```

### Kotlin/Native ↔ Objective-C Interop

Communication with UIKit uses the interop annotations:
- `@ObjCAction` — marks methods called through selectors (gesture targets)
- `sel_registerName("onTap")` — registers the selector for the action
- `@OptIn(ExperimentalForeignApi::class)` — required for interop APIs

## Building and running

```bash
cd fontes

# Build the framework for the simulator
./gradlew :view-native-ios:linkDebugFrameworkIosSimulatorArm64

# Deploy to the iPhone simulator
cd br.com.wdc.kt.shopping/br.com.wdc.kt.shopping.view.native/native.ios
./deploy.sh simulator --simulator-name "iPhone 17"

# Deploy to the iPad simulator
./deploy.sh simulator --ipad
```

The Xcode project (`ShoppingNativeApp.xcodeproj`) is generated with [XcodeGen](https://github.com/yonaskolb/XcodeGen) from `project.yml`.

## File Structure

```
src/iosMain/kotlin/br/com/wdc/shopping/nativeui/ios/
├── theme/
│   ├── Colors.kt          # Material Design palette adapted to UIColor
│   ├── Icons.kt           # SVG path icons → UIBezierPath
│   └── UIK.kt             # UIKit constants (alignments, content modes)
├── toolkit/
│   ├── AbstractViewIos.kt       # Base class + ListSlot + ViewSlot
│   ├── UIKitDom.kt              # DSL for building UIViews
│   ├── FlowLayoutView.kt        # Constraint-based responsive grid
│   ├── ViewUpdateScheduler.kt   # Update coalescing via GCD
│   └── ViewUtils.kt             # Formatting, async image loading
└── views/
    ├── RootViewIos.kt           # Entry point + factory registration
    ├── LoginViewIos.kt          # Login screen
    ├── HomeViewIos.kt           # Responsive layout with panels/tabs/detail
    ├── ProductsPanelViewIos.kt  # Product grid (FlowLayout)
    ├── PurchasesPanelViewIos.kt # Paginated purchase list
    ├── ProductViewIos.kt        # Product details
    ├── CartViewIos.kt           # Shopping cart
    └── ReceiptViewIos.kt        # Purchase receipt

app/
├── AppDelegate.swift        # Swift entry point (Kotlin framework bootstrap)
├── Info.plist               # App configuration (orientations, ATS)
└── Assets.xcassets/         # App icon
```
