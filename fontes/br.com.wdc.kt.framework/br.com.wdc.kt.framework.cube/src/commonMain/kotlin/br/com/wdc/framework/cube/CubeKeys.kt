package br.com.wdc.framework.cube

/**
 * The base of an application's keys classes: typed properties over a [CubeIntent], one class per place.
 *
 * - **Each keys class declares its place**, in its companion, from the place of the keys it extends
 *   (`val PLACE = HomeKeys.PLACE.child("product", ::ProductPresenter)`; the root keys use [CubeTreePlace.root]). So
 *   the class tree is the place tree, and a deeper presenter reads, through inherited properties, what the levels
 *   above hand down. Every place has its keys, even one with no names of its own: the keys declare it and navigate
 *   to it.
 * - **Built without an intent**, the keys start from the application's current state ([CubeApplication.newIntent],
 *   which asks every live presenter what it shows) and [flip] goes to their place:
 *   `ProductKeys(app).apply { productId = id }.flip()`.
 * - **Built over the intent a presenter receives**, they read and write it in place. A redirect from inside
 *   `applyParameters` uses the intent in transit — `HomeKeys(app, intent).flip()` — not a new one: that presenter is
 *   not accepted yet, and a new intent would lose what the address just brought.
 *
 * @param intent the intent to read and write; `null` for a new one, from the current state
 * @param place the place of the keys class being built: where [flip] goes
 */
abstract class CubeKeys<A : CubeApplication>(
    protected val app: A,
    intent: CubeIntent?,
    /** The place of these keys: where [flip] goes. */
    val place: CubeTreePlace,
) {

    /** The intent these keys read and write. */
    val intent: CubeIntent = intent ?: app.newIntent().also { it.place = place }

    /** Navigates to [place], with what was set on [intent]. */
    open suspend fun flip(): Boolean = app.flip(place, intent)
}
