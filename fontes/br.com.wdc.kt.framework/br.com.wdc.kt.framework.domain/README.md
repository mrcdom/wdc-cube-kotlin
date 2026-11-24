# framework-domain

Data access contracts, independent of database and transport (KMP).

- `repository` / `pagination` — `ReadOnlyRepository<E, C, K>` (queries), `Repository<E, C, K>` (queries and writes) and `Page`.
- `criteria` — `Criterion`, `ComparableCriterion`, `TextCriterion`, `Operator` and the `CriterionCodec`, which carries them to and from JSON.
- `projection` — `ProjectionValues`, `ProjectionList` and the projected collection envelope.
- `codec` — `ModelCodec`, the contract for reading and writing an entity and its criteria.
- `transaction` — `TransactionService` and `TransactionContext`.
- `resource` — `ResourceCall` and its verbs, the presentation's operations on the data ([below](#resource-calls)).
- `exception` — `BusinessException`, `InvalidRequestException`, `AccessDeniedException` and the transaction ones.

**Platforms:** JVM, Android, iOS, JS, wasmJs. Published on Maven Central as `io.github.mrcdom.wdc.kt:framework-domain`.

## Resource calls

One operation of the presentation on the data is a class named by what it does with the resource, in the spirit of the HTTP methods. A new instance per use: the request is the constructor's (named) parameters, the response comes from `run()`.

| Suffix | HTTP | Base | Transaction |
|---|---|---|---|
| `Query` | GET (safe, idempotent) | `ResourceQuery` | none of its own; reads in the caller's, if any |
| `Create` | POST | `ResourceCreate` | `run()` opens one around `create()` |
| `Save` | PUT (idempotent) | `ResourceSave` | `run()` opens one around `save()` |
| `Update` | PATCH | `ResourceUpdate` | `run()` opens one around `update()` |
| `Delete` | DELETE (idempotent) | `ResourceDelete` | `run()` opens one around `delete()` |

The four write verbs are closed (their base, `ResourceWrite`, can't be extended outside the module), so every write runs in a transaction: their final `run()` opens `required` on the `TransactionService` the constructor's function gives, read when the call runs; without one (a test without persistence), the write runs directly. What fits no verb (a credentials check, say) extends `ResourceCall` directly.

```kotlin
class ProductQuery(private val productId: Long) : ResourceQuery<ProductInfo>() {
    override suspend fun run(): ProductInfo =
        ProductInfo.create(ProductRepository.BEAN.get().fetchById(productId, ProductInfo.projection())
            ?: throw ProductNotFoundException())!!
}

class PurchaseCreate(private val userId: Long, private val cartItems: List<CartItem>) :
    ResourceCreate<Long>({ ShoppingTransactions.BEAN.getOrNull() }) {
    override suspend fun create(): Long { /* inserts the purchase and its items */ }
}

val product = ProductQuery(productId = id).run()
```

See the [persistence architecture](../../../docs/architecture-persistence.md).
