# native.ios

View implementation using **UIKit** via Kotlin/Native — no SwiftUI, no Storyboards, purely programmatic.

## Architecture

### Base Class: `AbstractViewIos<S>`

Every view extends `AbstractViewIos<S>`. The framework knows only scopes: this module is the UIKit rendering
technology, and it owns its views. A view receives the application (`IosNativeRootPresenter`) and the scope it paints
— never the presenter — and calls the scope's actions (`scope.onX(...)`) inside `safeAction`:

```kotlin
class ProductsPanelViewIos(app: IosNativeRootPresenter, scope: ProductsPanelScope)
    : AbstractViewIos<ProductsPanelScope>("products-panel-view", app, scope) {

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
2. `doUpdate()` — called once at creation and then by the scheduler (`redraw()`) whenever the scope asks for a repaint
3. Guards (`lastX != newX`) avoid unnecessary UIKit manipulation
4. `release()` — when no view holds it any more (below): takes its updater back, removes its `rootView` from the
   superview, releases its list slots and GC-retained targets, runs the `onRelease()` hook for the view's own cleanup
   (`HomeViewIos` removes its orientation observer there), and drops the views it held. A released view is never
   marked dirty or drawn again

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

Available components: `vStack`, `hStack`, `view`, `scrollView`, `flowLayout`, `label`, `button`, `textField`, `imageView`, `activityIndicator`, `segmentedControl`, `spacer`, `flexSpacer`.

All views use `translatesAutoresizingMaskIntoConstraints = false` (set automatically by the DSL) and are positioned with Auto Layout constraints.

### View Updates: `ViewUpdateScheduler`

The scheduler works via `dispatch_async(dispatch_get_main_queue())`:

1. A scope change calls the scope's updater (the view's `update()`) → marks the view as dirty
2. On the next run loop iteration, the scheduler flushes
3. Each flush first runs `app.commitComputedState()` once (every live presenter's; views it marks dirty join the same flush)
4. Then each dirty view runs `doUpdate()` once (`redraw()`)

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

Parent views declare a `ViewSlot` over a container `UIView` (`newViewSlot(container)`). The parent scope holds the
child's scope (`content`, and `productsPanel`/`purchasesPanel` in Home); while it draws, the parent asks for that
scope's view with its own `viewOf(scope)`, which goes to the application (`IosNativeRootPresenter.viewOf`): the view is
created once per scope (from the catalog, scope class → view factory), its updater installed in the scope, drawn once
right away, and reused while the scope is on screen. The slot pins the child view with constraints on all 4 edges:

```kotlin
// In RootViewIos
contentSlot = newViewSlot(container)

// In doUpdate(): the slot swaps the visible view when the child scope changes
contentSlot.sync(scope.content?.let(::viewOf))
```

**Releasing.** A scope leaves the screen because a field of its parent changed, which redraws the parent's view.
`redraw()` records the views asked for in that drawing; afterwards, the children not asked for are dropped. Each view
keeps its `children` and `holders`, and is released only when no view holds it — in cascade, with the views it held.
So **every `doUpdate()` asks for all the views it holds, on every drawing**, not only when they change (the cart
calls `rows.map(::viewOf)` outside its "rows changed" guard). The presenters take no part.

The cart draws each row (`CartScope.rows`) with the view of the row's scope (`CartItemViewIos`), so changing a
quantity repaints that row and the total, not the list. The cards inside list slots (`ProductCardView`,
`PurchaseCardView`) are `AbstractViewIos` items created by the `ListSlot`, not by the catalog.

The application (`IosNativeRootPresenter`) holds the catalog, the live views (`viewOf`, `forgetView`, `liveViews()`);
`AppBootstrap.kt` mounts `app.viewOf(app.scope).rootView`.

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
cd br.com.wdc.kt.store/br.com.wdc.kt.store.view.native/native.ios
./deploy.sh simulator --simulator-name "iPhone 17"

# Deploy to the iPad simulator
./deploy.sh simulator --ipad
```

`--backend-url <url>` (default `http://localhost:8080`) reaches the app as the `BACKEND_URL` environment variable,
which `app/AppDelegate.swift` reads: `deploy.sh` launches the app with `SIMCTL_CHILD_BACKEND_URL`, since the simulator
passes the app only the variables prefixed with `SIMCTL_CHILD_`. An app opened from its icon gets no variable and uses
the default. `deploy.sh` shows the simulator in Simulator.app or, on Xcode 27 (which replaced it), in DeviceHub.

## Texts and session

Views take `messages` from the application (`IosNativeRootPresenter.messages`, in the first of
`NSLocale.preferredLanguages`, US English when there is no catalog for it) and translate the `Message`s presenters put
in scopes. The saved session lives on the Keychain (`IosPersistentSessionStorage`).

The Xcode project (`StoreNativeApp.xcodeproj`) is generated with [XcodeGen](https://github.com/yonaskolb/XcodeGen) from `project.yml`.

## File Structure

```
src/iosMain/kotlin/br/com/wdc/store/nativeui/ios/
├── AppBootstrap.kt        # Entry point: platform setup; mounts the root scope's view
├── IosNativeRootPresenter.kt # The application: view catalog, live views, viewOf
├── theme/
│   ├── Colors.kt          # Palette from the design tokens, as UIColor
│   ├── Dimensions.kt      # Int.pt: token dimensions (StoreTokens spacing, radius, font size) as UIKit points
│   ├── Icons.kt           # SVG path icons → UIBezierPath
│   ├── Styles.kt          # Style helpers (StoreStyles)
│   └── UIKitConstants.kt  # UIKit constants (object UIK: alignments, content modes)
├── toolkit/
│   ├── AbstractViewIos.kt       # Base class + ListSlot + ViewSlot
│   ├── UIKitDom.kt              # DSL for building UIViews
│   ├── FlowLayoutView.kt        # Constraint-based responsive grid
│   ├── ViewUpdateScheduler.kt   # Update coalescing via GCD
│   └── ViewUtils.kt             # Formatting, async image loading
└── views/
    ├── RootViewIos.kt           # Page frame: content slot and error alert
    ├── LoginViewIos.kt          # Login screen
    ├── HomeViewIos.kt           # Responsive layout with panels/tabs/detail
    ├── ProductsPanelViewIos.kt  # Product grid (FlowLayout)
    ├── PurchasesPanelViewIos.kt # Paginated purchase list
    ├── ProductViewIos.kt        # Product details
    ├── CartViewIos.kt           # WDC Store cart: hosts the view of each row
    ├── CartItemViewIos.kt       # A cart row: quantity controls and remove
    └── ReceiptViewIos.kt        # Purchase receipt

app/
├── AppDelegate.swift        # Swift entry point: starts the Kotlin side once (the root view controller)
├── SceneDelegate.swift      # The app's window, in its scene (iOS 27 requires the scene life cycle)
├── Info.plist               # App configuration (orientations, ATS)
└── Assets.xcassets/         # App icon
```
