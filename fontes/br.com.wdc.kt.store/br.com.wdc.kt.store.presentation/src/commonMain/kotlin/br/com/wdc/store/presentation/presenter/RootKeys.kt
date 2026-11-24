package br.com.wdc.store.presentation.presenter

import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubeKeys
import br.com.wdc.framework.cube.CubeTreePlace
import br.com.wdc.framework.cube.ScopeSlot

/**
 * The names a place reads from and writes to the [CubeIntent], typed, on the framework's [CubeKeys]. **Each place has
 * one keys class, which declares the place and is the only one that knows its names** — even a place with no names
 * of its own yet; presenters never read or write the intent with a literal name, and navigate through the keys of
 * the destination (`CartKeys(app).flip()`).
 *
 * - **A two-letter prefix per class, unique in the application** ([PREFIX]); every name it declares starts with it
 *   (`pr-id`). Names live in a private table: `P` for parameters (they go in the address), `A` for attributes (they
 *   don't). To create a name, look only at your own file. `PlaceKeysTest` checks the prefixes are unique.
 * - **The keys declare the place**, in the companion, from the place of the keys they extend
 *   (`val PLACE = HomeKeys.PLACE.child("product", ::ProductPresenter)`), so the class tree is the place tree:
 *   `RootKeys` → `OpenKeys` → `LoginKeys`, and `RootKeys` → `RestrictedKeys` → `HomeKeys` → `ProductKeys`. The open
 *   and restricted places are guards: no scope, no view, only the rule of their area (a user, or none). A name is declared once, in the keys of whoever publishes it; the places
 *   below inherit it to read. [RootPresenter.PLACES] lists them all; `PlaceKeysTest` checks the tree.
 * - **The one name reused on purpose is the composition slot** ([parentSlot]): each level reads the slot it got and
 *   republishes, under the same name, the slot of its content for the level below.
 * - **Validation is in the getter, which never throws**: the address is input from outside. A getter returns a usable
 *   value or `null`. A setter given the default (or `null`) removes the name from the address.
 * - **Navigating starts from the state:** without an intent, the keys start from [RootPresenter.newIntent],
 *   which asks every live presenter what it shows — `ProductKeys(app).apply { productId = id }.flip()`.
 * - **Redirecting from inside `applyParameters` uses the intent in transit** (`HomeKeys(app, intent).flip()`), not a new
 *   one: that presenter is not accepted yet, and a new intent would lose what the address just brought.
 */
open class RootKeys protected constructor(app: RootPresenter, intent: CubeIntent?, place: CubeTreePlace) :
    CubeKeys<RootPresenter>(app, intent, place) {

    constructor(app: RootPresenter, intent: CubeIntent? = null) : this(app, intent, PLACE)

    companion object {
        const val PREFIX = "rt"

        /** The root: the application itself (the page frame); alone, it goes on to the restricted area. */
        val PLACE: CubeTreePlace = CubeTreePlace.ROOT
    }

    private object A {
        /** Where the level below shows its view; each level republishes it for the next. */
        const val PARENT_SLOT = "$PREFIX-parent-slot"
    }

    /** The slot where this level shows its view; a level with content republishes it for the level below. */
    var parentSlot: ScopeSlot?
        get() = intent.getScopeSlot(A.PARENT_SLOT)
        set(value) = intent.setScopeSlot(A.PARENT_SLOT, value)
}
