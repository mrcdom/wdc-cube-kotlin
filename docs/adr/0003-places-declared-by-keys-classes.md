# 0003. Places are declared by keys classes, in a tree

- **Date:** 2026-11-22
- **Status:** Accepted

## Context

Navigation in the Cube goes to a *place*: a path of presenters from the root to a screen, with parameters carried in an intent and published in the address (`product?pr-id=4`). The place list used to live in a separate routes table and an enum, the parameter names were string literals spread over presenters, and a typo in a name compiled and failed at run time. The C# port of the architecture found the same weakness and solved it with keys classes (its ADR 0017); the maintainer asked for the same in all three versions.

## Decision

Each place has one keys class, `<Place>Keys`, even when it has no parameters of its own. The class declares the place in a companion object, as a child of its parent's place, with the presenter that runs it:

```kotlin
class ProductKeys(app: RootPresenter, intent: CubeIntent? = null) : HomeKeys(app, intent, PLACE) {
    companion object {
        const val PREFIX = "pr"
        val PLACE: CubeTreePlace = HomeKeys.PLACE.child("product", ::ProductPresenter)
    }

    private object P {
        const val PRODUCT_ID = "$PREFIX-id"
    }

    var productId: Long?
        get() = intent.getParameterAsLong(P.PRODUCT_ID)?.takeIf { it > 0 }
        set(value) = intent.setParameter(P.PRODUCT_ID, value?.takeIf { it > 0 })
}
```

The places form a tree (`CubeTreePlace`) whose ids grow from the root to the leaves. A keys class of a place with children has a `protected` constructor that takes the child's place, so the child's keys extend it. Navigation goes only through keys: `ProductKeys(app).apply { productId = 4 }.flip()`. Parameter names start with a two-letter prefix unique to the place (`pr-`, `rc-`, `hm-`). The application resolves an address by place name (`app.flip(address)`); an unknown name goes to the root.

## Consequences

- A presenter never writes a parameter name as a literal; renaming one is a refactoring the compiler checks.
- Access control in the presentation is structural: every restricted screen is below `RestrictedKeys`, whose presenter redirects a user without a session (see [architecture-cube.md](../architecture-cube.md), guard places).
- The identity of the user never goes in the address; it comes from the session.
- Changing a parameter's name breaks old links. When the names were introduced, the maintainer chose to accept that rather than keep aliases; a missing or invalid id redirects to the Home.
- `PlaceKeysTest` checks the tree by reflection (each keys class's `PLACE`), and reads the keys sources to check that prefixes are unique and that every name starts with its prefix.

## Alternatives considered

- **A routes table plus string names** (what existed). Simple, but nothing tied a name to its place, and the tree was implicit.
