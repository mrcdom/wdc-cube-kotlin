# native.web

View implementation using **React + Material UI** through Kotlin/JS — no Compose Web, no Wasm.

## Architecture

### Base Class: `ReactCubeView<S>`

Every view extends `ReactCubeView<S>`, which ties the Cube lifecycle to React's rendering mechanism. A view receives
the application and the scope it paints — never the presenter — and calls the scope's actions (`scope.onX(...)`)
inside `safeCall`:

```kotlin
class ProductsPanelView(app: RootPresenter, scope: ProductsPanelScope)
    : ReactCubeView<ProductsPanelScope>("products-panel-view", app, scope) {

    override val component = FC<Props> {
        var rev by useState(revision)
        useEffect(this@ProductsPanelView) { onUpdate = { rev = revision } }
        // JSX through Kotlin/JS wrappers
        Typography { +"Products" }
        Box { /* ... */ }
    }
}
```

The lifecycle is:
1. `component` — the React function component that produces the UI through React/MUI, reading the scope
2. `ViewUpdateScheduler` increments the view's `revision` and calls its `onUpdate` → the component's state changes → re-render
3. Since React is already reactive, there is no need for manual guards (the virtual DOM optimizes automatically)

### Technology Stack

| Layer | Technology |
|--------|-----------|
| **Language** | Kotlin/JS (compiled to JavaScript) |
| **UI Library** | React 19 (through kotlin-wrappers) |
| **Component Library** | Material UI 5 (MUI) |
| **Bundler** | Webpack (through the Gradle plugin) |
| **Routing** | Hash-based (`#home`, `#product?pr-id=1`, signed with HMAC as in compose.web) through Cube navigation |

### Cube ↔ React Integration

The bridge between the Cube framework (imperative) and React (declarative) is the `ViewUpdateScheduler`:

1. A scope change calls the view's `update()` → marks the view as dirty
2. On the next animation frame (`window.requestAnimationFrame`), the scheduler flushes; a `safeCall` also flushes when its action ends
3. The flush first runs `app.commitComputedState()` once (every live presenter's), then increments the `revision` (React state) of each dirty view
4. React re-renders only the components whose state changed

### Implementation Patterns

| Pattern | Description |
|--------|-----------|
| **RenderSlot** | React component that renders a child `CubeView` (navigation) |
| **Responsive breakpoints** | `window.innerWidth` against 518px (`COMPACT_BREAKPOINT`, in `HomeView`) to switch between compact and wide layouts |
| **Hash routing** | `window.location.hash` kept in sync with Cube navigation (in `Main.kt`) |
| **MUI theming** | Colors, styles and the MUI theme through constants (`StoreColors`, `StoreStyles`, `StoreTheme`) |
| **useEffect + revision** | Each view's component keeps `useState(revision)` and sets the view's `onUpdate` in a `useEffect`, to trigger a re-render |

### Navigation and Slots

Parent views use the `RenderSlot` component. The parent scope holds the child's scope (`content`, and
`productsPanel`/`purchasesPanel` in Home); the parent asks the application for that scope's view, created once per
scope and reused:

```kotlin
// In HomeView
val contentView = scope.content?.let(app::viewOf)
RenderSlot { view = contentView }

// When the presenter navigates, the scope in the field changes
// and RenderSlot renders the new view automatically
```

The cart draws each row (`CartScope.rows`) with the view of the row's scope (`CartItemView`), so changing a quantity
repaints that row and the total, not the list. The catalog of views (`ScopeViews`, scope class → view) is set on the
application in `Main.kt` before the first navigation.

`RenderSlot` is memoized: a parent repaint does not repaint the view in its slot. Cube views are independent of each other — a view repaints itself when its own scope changes (its component subscribes to `onUpdate`), and the slot repaints only when the parent puts a different view in it.

### Emitters (event handlers)

Event handlers are properties of the view class, created once per view — the same function on every render, like the `readonly emitXxx` fields of the remote React client:

```kotlin
class ReceiptView(app: RootPresenter, scope: ReceiptScope) : ReactCubeView<ReceiptScope>("receipt-view", app, scope) {
    private val onOpenProducts: MouseEventHandler<HTMLButtonElement> = { safeCall { scope.onOpenProducts() } }

    override val component = FC<Props> {
        Button { onClick = onOpenProducts }
    }
}
```

- An emitter does not capture values of the render: it reads what it needs from the scope, from a field of the view, or from an element ref, when it runs.
- UI state that belongs to the view (a quantity being chosen, the selected tab) lives in a field; the emitter changes it and calls `forceUpdate()`, which repaints this view only.
- List items get their own emitters, kept per key by `EmitterCache` (the products panel: `openProduct[product.id]`); `retainOnly(keys)` drops the keys no longer shown. A list whose rows have actions of their own (the cart) is better drawn as one view per row scope (`CartItemView`), whose emitters are fields like any view's.
- Text fields are uncontrolled where the value is only needed on submit (login): `inputRef` + `defaultValue`, read when the emitter runs, so typing repaints nothing.

## How to build and run

```bash
cd fontes

# Development with hot reload (port 8083; /api goes to the backend on 8080)
./gradlew :view-native-web:jsBrowserDevelopmentRun --continuous

# Production build
./gradlew :view-native-web:jsBrowserDistribution
```

`./deploy.sh` builds the production bundle and copies it, with gzip copies of the JS and CSS, to
`fontes/work/deployment/native/`; the backend then serves it at `http://localhost:8080/native/`.

## File Structure

```
src/jsMain/kotlin/br/com/wdc/store/nativeui/web/
├── Main.kt                # Entry point — bootstrap, the view catalog (ScopeViews), signed hash routing
├── bridge/
│   ├── ReactCubeView.kt        # Base class of the views (revision, onUpdate, safeCall, forceUpdate)
│   ├── ViewUpdateScheduler.kt  # Update coalescing per animation frame
│   ├── RenderSlot.kt           # Memoized slot for a child view
│   └── EmitterCache.kt         # Per-key event handlers for list items
├── theme/
│   └── StoreTheme.kt      # Palette (StoreColors), styles (StoreStyles) and the MUI theme
├── util/
│   └── FormatUtils.kt     # Price and date formatting, image URLs
└── views/
    ├── RootView.kt              # App shell
    ├── LoginView.kt             # Login screen
    ├── HomeView.kt              # Responsive layout with panels/tabs
    ├── ProductsPanelView.kt     # Product grid
    ├── PurchasesPanelView.kt    # Paginated purchase list
    ├── ProductView.kt           # Product detail
    ├── CartView.kt              # Cart and its row view (CartItemView)
    └── ReceiptView.kt           # Purchase receipt
```
