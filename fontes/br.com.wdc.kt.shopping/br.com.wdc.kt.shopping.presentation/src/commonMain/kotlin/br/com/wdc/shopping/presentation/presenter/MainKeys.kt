package br.com.wdc.shopping.presentation.presenter

import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubeViewSlot
import br.com.wdc.shopping.presentation.ShoppingApplication

/**
 * The names a place reads from and writes to the [CubeIntent], typed. **Each place has one keys class, the only
 * one that knows its names** — even a place with no names of its own yet; presenters never read or write the intent
 * with a literal name, and navigate through the keys of the destination.
 *
 * - **A two-letter prefix per class, unique in the application** ([PREFIX]); every name it declares starts with it
 *   (`pr-id`). Names live in a private table: `P` for parameters (they go in the address), `A` for attributes (they
 *   don't). To create a name, look only at your own file. `PlaceKeysTest` checks the prefixes are unique.
 * - **Inheritance follows the place tree:** `MainKeys` → `HomeKeys` → `ProductKeys`. A name is declared once, in the
 *   keys of whoever publishes it; the places below inherit it to read.
 * - **The one name reused on purpose is the composition slot** ([parentSlot]): each level reads the slot it got and
 *   republishes, under the same name, the slot of its content for the level below.
 * - **Validation is in the getter, which never throws**: the address is input from outside. A getter returns a usable
 *   value or `null`. A setter given the default (or `null`) removes the name from the address.
 * - **Navigating starts from the state:** without an intent, the keys start from [ShoppingApplication.newIntent],
 *   which asks every live presenter what it shows — `ProductKeys(app).apply { productId = id }.go()`.
 * - **Redirecting from inside `applyParameters` uses the intent in transit** (`HomeKeys(app, intent).go()`), not a new
 *   one: that presenter is not accepted yet, and a new intent would lose what the address just brought.
 */
open class MainKeys(protected val app: ShoppingApplication, intent: CubeIntent? = null) {

    /** The intent these keys read and write. */
    val intent: CubeIntent = intent ?: app.newIntent()

    companion object {
        const val PREFIX = "mn"
    }

    private object A {
        /** Where the level below shows its view; each level republishes it for the next. */
        const val PARENT_SLOT = "$PREFIX-parent-slot"
    }

    /** Navigates to this keys' place, with this intent. */
    open suspend fun go(): Boolean = Routes.root(app, intent)

    /** The slot where this level shows its view; a level with content republishes it for the level below. */
    var parentSlot: CubeViewSlot?
        get() = intent.getViewSlot(A.PARENT_SLOT)
        set(value) = intent.setAttribute(A.PARENT_SLOT, value)
}
