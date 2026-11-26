# native.android

View implementation using **programmatic Android Views** — no XML layouts, no Compose, no ViewBinding.

## Architecture

### Base Class: `AbstractViewAndroid<S>`

Every view extends `AbstractViewAndroid<S>`. The framework knows only scopes: this module is the Android Views
rendering technology, and it owns its views. A view receives the application (`AndroidNativeRootPresenter`) and the
scope it paints — never the presenter — and calls the scope's actions (`scope.onX(...)`) inside `safeAction`:

```kotlin
class ProductsPanelViewAndroid(app: AndroidNativeRootPresenter, scope: ProductsPanelScope)
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
2. `doUpdate()` — called once at creation and then by the scheduler (`redraw()`) whenever the scope asks for a repaint
3. Guards (`lastX != newX`) avoid unnecessary recomputation
4. `release()` — when no view holds it any more (below): takes its updater back, removes its `rootView` from its
   parent, releases its list slots, runs the `onRelease()` hook for the view's own cleanup, and drops the views it
   held. A released view is never marked dirty or drawn again

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

1. A scope change calls the scope's updater (the view's `update()`) → marks the view as dirty
2. On the next main-looper pass, the scheduler flushes
3. Each flush first runs `app.commitComputedState()` once (views it marks dirty join the same flush)
4. Then each dirty view runs `doUpdate()` once (`redraw()`)

### Implementation Patterns

| Pattern | Description |
|--------|-----------|
| **ListSlot** | View recycling in lists — grows, shrinks and updates in place |
| **FlowLayout** | Custom ViewGroup that lays out children in a responsive grid based on the available width |
| **StateListDrawable** | Visual touch feedback (the background changes color in the `pressed` state) |
| **Guards** | `lastX` variables that avoid reprocessing when the state has not changed |
| **Density-aware** | All dimensions use `* density` for density independence |

### Navigation and Slots

Parent views declare a `ViewSlot` over a `FrameLayout` (`newViewSlot(container)`). The parent scope holds the child's
scope (`content`, and `productsPanel`/`purchasesPanel` in Home); while it draws, the parent asks for that scope's view
with its own `viewOf(scope)`, which goes to the application (`AndroidNativeRootPresenter.viewOf`): the view is created
once per scope (from the catalog, scope class → view factory), its updater installed in the scope, drawn once right
away, and reused while the scope is on screen:

```kotlin
// In RootViewAndroid
contentSlot = newViewSlot(container)

// In doUpdate(): the slot swaps the visible view when the child scope changes
contentSlot.sync(scope.content?.let(::viewOf))
```

A child view repaints only when its own scope changes, never along with its parent.

**Releasing.** A scope leaves the screen because a field of its parent changed, which redraws the parent's view.
`redraw()` records the views asked for in that drawing; afterwards, the children not asked for are dropped. Each view
keeps its `children` and `holders`, and is released only when no view holds it — in cascade, with the views it held.
So **every `doUpdate()` asks for all the views it holds, on every drawing**, not only when they change (the cart
calls `rows.map(::viewOf)` outside its "rows changed" guard). The presenters take no part.

The application (`AndroidNativeRootPresenter`) holds the catalog and the live views (`viewOf`, `forgetView`,
`liveViews()`). `MainActivity` shows `app.viewOf(app.scope).rootView`, keeps that root view and releases it in
`onDestroy()`; the cascade takes the rest.

## Building and running

```bash
cd fontes

# Build the debug APK
./gradlew :view-native-android:assembleDebug

# Deploy to the emulator
cd br.com.wdc.kt.store/br.com.wdc.kt.store.view.native/native.android
./deploy.sh emulator --emulator-name Pixel_10
```

The backend URL is baked into the APK as `BuildConfig.BASE_URL` at build time (`-PbaseUrl=<url>`; default
`http://10.0.2.2:8080`, the host as seen from the emulator). `deploy.sh` (`emulator` or `device`) takes:

- `--backend-url <url>` — passed to the build as `-PbaseUrl` (default `http://10.0.2.2:8080` on the emulator,
  `http://<host-ip>:8080` on a device); it has no effect with `--skip-build`, since the APK is not rebuilt;
- `--emulator-name <name>` — the AVD (default: interactive selection);
- `--cold-boot` — restarts the emulator from scratch: shuts it down if it is running, then boots it without loading or saving a snapshot (the cure when the emulator's System UI stops responding);
- `--netspeed <speed>` / `--netdelay <delay>` — the emulator's network speed and latency;
- also `--release`, `--skip-build`, `--clean`, `--fresh` (see `./deploy.sh --help`).

## Texts and session

Views take `messages` from the application (`AndroidNativeRootPresenter.messages`, in the language of
`Locale.getDefault()`, US English when there is no catalog for it; `AbstractViewAndroid.messages`) and translate the
`Message`s presenters put in scopes (`translate(message)`). The saved session is encrypted with AES-GCM under a key
kept in the Android Keystore (`AndroidPersistentSessionStorage`).

## File Structure

```
src/main/kotlin/br/com/wdc/store/nativeui/android/
├── MainActivity.kt        # Entry point: bootstrap; shows and releases the root scope's view
├── AndroidNativeRootPresenter.kt # The application: view catalog, live views, viewOf
├── AndroidScheduledExecutor.kt
├── theme/
│   ├── Colors.kt          # Palette from the design tokens (TokenColors.argbInt(StoreTokens.Color…))
│   ├── Dimens.kt          # The token scale (StoreTokens), local sizes and the 480dp breakpoint
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
