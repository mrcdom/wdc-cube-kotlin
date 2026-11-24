package br.com.wdc.framework.domain.resource

import br.com.wdc.framework.domain.transaction.TransactionService

/**
 * One operation of the presentation on the data, named by what it does with the resource, in the spirit of the HTTP
 * methods: a new instance per use, the request in its constructor's (named) parameters, the response from [run].
 *
 * | Suffix | HTTP | Base | Transaction |
 * |---|---|---|---|
 * | `Query` | GET (safe, idempotent) | [ResourceQuery] | none of its own; reads in the caller's, if any |
 * | `Create` | POST | [ResourceCreate] | [run] opens one around [ResourceCreate.create] |
 * | `Save` | PUT (idempotent) | [ResourceSave] | [run] opens one around [ResourceSave.save] |
 * | `Update` | PATCH | [ResourceUpdate] | [run] opens one around [ResourceUpdate.update] |
 * | `Delete` | DELETE (idempotent) | [ResourceDelete] | [run] opens one around [ResourceDelete.delete] |
 *
 * What fits none gets a verb of its own and extends [ResourceCall] directly (a credentials check, say). The four
 * write verbs are closed: [ResourceWrite] can't be extended outside this module, so every write runs in a
 * transaction.
 *
 * ```kotlin
 * class ProductQuery(private val productId: Long) : ResourceQuery<ProductInfo>() {
 *     override suspend fun run(): ProductInfo = …
 * }
 * val product = ProductQuery(productId = id).run()
 * ```
 */
abstract class ResourceCall<R> {
    abstract suspend fun run(): R
}

/** A read: no transaction of its own (it reads in the caller's, if there is one). */
abstract class ResourceQuery<R> : ResourceCall<R>()

/**
 * A write: [run] opens a transaction (`required`) with the module's [TransactionService], read when it runs, and
 * calls the verb's method inside it. Without a transaction service (a test without persistence), the write runs
 * directly.
 */
abstract class ResourceWrite<R> internal constructor(
    private val transactions: () -> TransactionService?,
) : ResourceCall<R>() {

    final override suspend fun run(): R {
        val service = transactions() ?: return write()
        return service.required { write() }
    }

    internal abstract suspend fun write(): R
}

/** Creates a resource (POST). */
abstract class ResourceCreate<R>(transactions: () -> TransactionService?) : ResourceWrite<R>(transactions) {
    final override suspend fun write(): R = create()

    protected abstract suspend fun create(): R
}

/** Writes a resource whole (PUT): repeating it gives the same result. */
abstract class ResourceSave<R>(transactions: () -> TransactionService?) : ResourceWrite<R>(transactions) {
    final override suspend fun write(): R = save()

    protected abstract suspend fun save(): R
}

/** Changes part of a resource (PATCH). */
abstract class ResourceUpdate<R>(transactions: () -> TransactionService?) : ResourceWrite<R>(transactions) {
    final override suspend fun write(): R = update()

    protected abstract suspend fun update(): R
}

/** Removes a resource (DELETE): repeating it gives the same result. */
abstract class ResourceDelete<R>(transactions: () -> TransactionService?) : ResourceWrite<R>(transactions) {
    final override suspend fun write(): R = delete()

    protected abstract suspend fun delete(): R
}
