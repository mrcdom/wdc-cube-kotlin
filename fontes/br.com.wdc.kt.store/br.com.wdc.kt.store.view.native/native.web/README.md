# native.web

View implementation using **React + Material UI** through Kotlin/JS — no Compose Web, no Wasm.

## Architecture

### Views: function components of the scope

A view is a React function component of the scope it paints — no class, no object behind it, as in the TypeScript
version of the framework. It receives the scope in its props (never the presenter), becomes its updater with
`useScope`, and calls the scope's actions (`scope.onX(...)`) inside `safeCall`:

```kotlin
val ProductsPanelView = FC<ScopeProps<ProductsPanelScope>> { props ->
    val scope = useScope(props.scope)
    // JSX through Kotlin/JS wrappers
    Typography { +"Products" }
    for (product in scope.products) {
        CardActionArea { onClick = { safeCall { scope.onOpenProduct(product.id) } } }
    }
}
```

`useScope(scope)` installs the component as the scope's updater (`scope.setUpdater`) as it starts rendering — before
it reads the scope, so no change is lost — and takes it back (`clearUpdater`) in the cleanup of a
`useEffectWithCleanup` when it unmounts: the component's lifecycle is the view's, React drops what leaves the screen,
and nothing is released by hand. A scope has one updater, so one component draws a scope at a time (the Home draws
each panel in its compact *or* its wide layout, never both).

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

1. A scope change calls its updater, the component that draws it (`useScope`) → it marks itself dirty
2. On the next animation frame (`window.requestAnimationFrame`), the scheduler flushes; a `safeCall` also flushes when its action ends
3. The flush first runs `app.commitComputedState()` once (every live presenter's), then bumps a state counter of each dirty component
4. React re-renders only those components

### Implementation Patterns

| Pattern | Description |
|--------|-----------|
| **RenderSlot** | Memoized component that draws the view of a child scope, from the catalog |
| **Responsive breakpoints** | `window.innerWidth` against 518px (`COMPACT_BREAKPOINT`, in `HomeView`) to switch between compact and wide layouts |
| **Hash routing** | `window.location.hash` kept in sync with Cube navigation (in `Main.kt`) |
| **MUI theming** | Colors, styles and the MUI theme through constants (`StoreColors`, `StoreStyles`, `StoreTheme`) |
| **useScope** | Each view is its scope's updater while mounted, to repaint when it changes |

### Navigation and Slots

Parent views use the `RenderSlot` component. The parent scope holds the child's scope (`content`, and
`productsPanel`/`purchasesPanel` in Home), and the slot draws it with the component the catalog registers for its
class:

```kotlin
// In HomeView
RenderSlot { this.scope = scope.content }

// When the presenter navigates, the scope in the field changes
// and RenderSlot renders the new view automatically
```

The catalog (`ScopeComponents`, scope class → component) is `storeViews`, in `views/StoreViews.kt`; `Main.kt`
provides it around the root (`ScopeComponentsContext.Provider`) and draws the root scope in a slot.

`RenderSlot` is memoized: a parent repaint does not repaint the view in its slot. Cube views are independent of each
other — a view repaints itself when its own scope changes, and the slot repaints only when the parent puts a different
scope in it. The slot keys the component by the scope, so another scope in the same slot is a new component, with
fresh UI state. The cart draws each row (`CartScope.rows`) in a slot of its own, keyed where the list calls it
(`key = scopeKey(row)`), so changing a quantity repaints that row and the total, not the list.
`RenderSlotTest` (jsTest, Chrome headless) covers this.

### Event handlers and UI state

- Event handlers are lambdas in the component: `onClick = { safeCall { scope.onBuy() } }`. They read what they need
  from the scope, from component state or from an element ref when they run.
- UI state that belongs to the view (a quantity being chosen, the selected tab) is component state (`useState`),
  fresh for each scope the slot shows.
- Text fields are uncontrolled where the value is only needed on submit (login): `useRef` + `inputRef` +
  `defaultValue`, read when the handler runs, so typing repaints nothing.
- A listener on the window (the Home's resize) is added in `useEffectWithCleanup` and removed when the view unmounts.

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
├── Main.kt                # Entry point — bootstrap, the catalog around the root, signed hash routing
├── bridge/
│   ├── RenderSlot.kt           # ScopeProps, the catalog (ScopeComponents), RenderSlot, useScope, safeCall
│   └── ViewUpdateScheduler.kt  # The paint cycle, once per animation frame
├── theme/
│   └── StoreTheme.kt      # Palette (StoreColors), styles (StoreStyles) and the MUI theme
├── util/
│   └── FormatUtils.kt     # Price and date formatting, image URLs
└── views/
    ├── StoreViews.kt            # The catalog: which component draws each scope
    ├── RootView.kt              # App shell
    ├── LoginView.kt             # Login screen
    ├── HomeView.kt              # Responsive layout with panels/tabs
    ├── ProductsPanelView.kt     # Product grid
    ├── PurchasesPanelView.kt    # Paginated purchase list
    ├── ProductView.kt           # Product detail
    ├── CartView.kt              # Cart and its row view (CartItemView)
    └── ReceiptView.kt           # Purchase receipt
```
