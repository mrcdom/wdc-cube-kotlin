package br.com.wdc.shopping.persistence.rest.doc

import br.com.wdc.framework.domain.criteria.ComparableCriterion
import br.com.wdc.framework.domain.criteria.Criteria
import br.com.wdc.framework.domain.criteria.Criterion
import br.com.wdc.framework.domain.criteria.Operator
import br.com.wdc.framework.domain.criteria.TextCriterion
import br.com.wdc.shopping.domain.product.ProductCriteria
import br.com.wdc.shopping.domain.purchase.PurchaseCriteria
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemCriteria
import br.com.wdc.shopping.domain.user.UserCriteria
import com.google.gson.GsonBuilder
import io.javalin.config.JavalinConfig

/**
 * A descrição OpenAPI da API de repositório, servida em `GET /openapi.json`.
 *
 * O que pode ser lido do domínio não é escrito aqui: os campos de cada `XxxCriteria` saem de
 * `Criteria.criterions()`, as ordenações do `OrderBy` da entidade e os operadores de [Operator]. Assim a
 * documentação não envelhece à parte do código — e o `OpenApiSpecTest` confere que continua assim.
 */
object RepositoryApiDocs {

    private class Entity(
        val name: String,
        val route: String,
        val label: String,
        val criteria: Criteria,
        val orderings: List<Enum<*>>,
        val properties: Map<String, Any>,
        val scope: String,
    )

    private val json: String by lazy { GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(document()) }

    fun configure(config: JavalinConfig) {
        config.routes.get("/openapi.json") { ctx ->
            ctx.contentType("application/json")
            ctx.result(json)
        }
    }

    fun toJson(): String = json

    // :: Documento

    private fun entities(): List<Entity> = listOf(
        Entity(
            "Product", "product", "product", ProductCriteria(), ProductCriteria.OrderBy.entries,
            mapOf(
                "id" to long("Primary key. Generated on insert when absent."),
                "name" to string("Product name."),
                "price" to number("Current price."),
                "description" to string("Long description (may contain HTML)."),
            ),
            "Everyone authenticated reads; writing requires `product:write`.",
        ),
        Entity(
            "User", "user", "user", UserCriteria(), UserCriteria.OrderBy.entries,
            mapOf(
                "id" to long("Primary key. Generated on insert when absent."),
                "userName" to string("Login."),
                "password" to string("**Write-only.** Accepted on insert and update; never returned, not even when projected.") +
                    mapOf("writeOnly" to true),
                "name" to string("Display name."),
                "roles" to string("Comma-separated roles (`ADMIN`, `MANAGER`, `CUSTOMER`)."),
            ),
            "Without `data:all`, a user only reaches their own record.",
        ),
        Entity(
            "Purchase", "purchase", "purchase", PurchaseCriteria(), PurchaseCriteria.OrderBy.entries,
            mapOf(
                "id" to long("Primary key. Generated on insert when absent."),
                "buyDate" to string("Instant of the purchase, ISO-8601 in UTC.") + mapOf("format" to "date-time"),
                "user" to ref("User", "The buyer. Only the key is needed on writes."),
                "items" to mapOf(
                    "description" to "The purchase items. **In a response** it is an array. **In a projection** it is either an " +
                        "array with one element (the shape of each item) or a `ProjectedCollection` envelope, which also " +
                        "filters, orders and slices the items. On insert, the items are written together with the purchase.",
                    "oneOf" to listOf(
                        mapOf("type" to "array", "items" to schemaRef("PurchaseItem")),
                        schemaRef("ProjectedCollection"),
                    ),
                ),
            ),
            "Without `data:all`, a user only reaches their own purchases; a purchase they insert is always theirs.",
        ),
        Entity(
            "PurchaseItem", "purchase-item", "purchase item", PurchaseItemCriteria(), PurchaseItemCriteria.OrderBy.entries,
            mapOf(
                "id" to long("Primary key. Generated on insert when absent."),
                "amount" to mapOf("type" to "integer", "format" to "int32", "minimum" to 1, "description" to "Quantity."),
                "price" to number("Price charged for one unit."),
                "product" to ref("Product", "The product. Only the key is needed on writes."),
                "purchaseId" to long("Key of the purchase the item belongs to. Absent when the item travels inside its purchase."),
            ),
            "Without `data:all`, a user only reads and writes items of their own purchases.",
        ),
    )

    private fun document(): Map<String, Any> {
        val entities = entities()
        val paths = LinkedHashMap<String, Any>()
        entities.forEach { paths.putAll(entityPaths(it)) }
        paths.putAll(imagePaths())
        paths.putAll(transactionPaths())
        paths.putAll(authPaths())

        val schemas = LinkedHashMap<String, Any>()
        schemas["Error"] = obj("An error response.", "error" to string("What went wrong."))
        schemas["SuccessResponse"] = obj("Outcome of a write.", "success" to boolean("Whether a row was written."))
        schemas["InsertResponse"] = obj(
            "Outcome of an insert.",
            "success" to boolean("Whether the row was inserted."),
            "id" to long("The key of the inserted row (`-1` when there is none)."),
        )
        schemas["CountResponse"] = obj("A number of rows.", "count" to mapOf("type" to "integer", "format" to "int32"))
        schemas["CriterionPredicate"] = criterionPredicate()
        schemas["Criterion"] = criterion()
        schemas["ProjectedCollection"] = projectedCollection()
        for (entity in entities) {
            schemas[entity.name] = mapOf(
                "type" to "object",
                "description" to "A ${entity.label}. In a **projection**, a present field means \"bring this field\" — its value is ignored.",
                "properties" to entity.properties,
            )
            schemas["${entity.name}Criteria"] = criteriaSchema(entity)
            schemas["${entity.name}FetchRequest"] = fetchRequest(entity, paged = false)
            schemas["${entity.name}FetchPageRequest"] = fetchRequest(entity, paged = true)
            schemas["${entity.name}List"] = obj("The rows found.", "items" to mapOf("type" to "array", "items" to schemaRef(entity.name)))
            schemas["${entity.name}Page"] = obj(
                "One page of rows.",
                "items" to mapOf("type" to "array", "items" to schemaRef(entity.name)),
                "totalItems" to mapOf("type" to "integer", "format" to "int32", "description" to "Rows matching the criteria, across all pages."),
            )
        }
        schemas["TransactionStarted"] = obj("A remote transaction.", "txId" to string("Send it back in `X-Tx-Id`."))
        schemas["TransactionStatus"] = obj(
            "State of a remote transaction.",
            "status" to mapOf("type" to "string", "enum" to listOf("open", "committed", "rolledback", "unknown")),
        )
        schemas["AuthTokens"] = obj(
            "An authenticated session.",
            "userId" to long("The user."),
            "accessToken" to string("JWT for the `Authorization: Bearer` header."),
            "refreshToken" to string("Exchange it for a new session at `/api/auth/refresh`."),
            "expiresAt" to string("When the access token expires.") + mapOf("format" to "date-time"),
            "publicKey" to string("RSA public key (Base64) to encrypt a password sent on a user write."),
            "intentSignKey" to string("Per-user key the web clients use to sign navigation intents."),
        )

        return linkedMapOf(
            "openapi" to "3.0.3",
            "info" to mapOf(
                "title" to "WeDoCode Shopping — Repository API",
                "version" to "1.0.0",
                "description" to """
                    REST access to the Shopping repositories. Every entity has the same operations, and the client
                    and the server read and write with the same codecs.

                    **Criteria.** Each filterable field is sent as a `Criterion`: an object carrying one or more
                    comparison requests. Requests on the same field combine with `AND`, or with `OR` when the field
                    sets `"or": true`; different fields always combine with `AND`. A bare value is shorthand for
                    equality: `{"name": "Ball"}`.

                    **Projection.** `projection` names the fields to bring: a present field is requested, an absent
                    one is not. Without it, each entity has a default. A to-many field may be a
                    `ProjectedCollection`, which filters, orders and slices the collection.

                    **Transactions.** Every write is atomic. To make several writes atomic, open a remote
                    transaction (`POST /api/tx/begin`), send its `X-Tx-Id` on the writes and finish with
                    `commit` or `rollback`. Reads do not join the transaction.

                    **Security.** When the server has a JWT secret, `/api/repo` and `/api/tx` require
                    `Authorization: Bearer <accessToken>` (the product image is public to read) and `/api/auth`
                    exists. Without the secret the API is open and `/api/auth` is not served.
                """.trimIndent(),
            ),
            "tags" to (entities.map { mapOf("name" to it.name, "description" to it.scope) } + listOf(
                mapOf("name" to "Transaction", "description" to "Client-driven remote transactions."),
                mapOf("name" to "Auth", "description" to "Challenge–response login. Only served when security is on."),
            )),
            "security" to listOf(mapOf("bearerAuth" to emptyList<String>()), emptyMap()),
            "paths" to paths,
            "components" to mapOf(
                "schemas" to schemas,
                "securitySchemes" to mapOf("bearerAuth" to mapOf("type" to "http", "scheme" to "bearer", "bearerFormat" to "JWT")),
                "parameters" to mapOf(
                    "TxId" to mapOf(
                        "name" to "X-Tx-Id", "in" to "header", "required" to false, "schema" to mapOf("type" to "string"),
                        "description" to "Joins the write to this remote transaction; the write is then confirmed only by the commit.",
                    ),
                    "ClientId" to mapOf(
                        "name" to "X-Client-Id", "in" to "header", "required" to false, "schema" to mapOf("type" to "string"),
                        "description" to "Identifies the client instance. It owns the remote transactions when there is no authenticated user.",
                    ),
                ),
            ),
        )
    }

    // :: Critério

    private fun criterionPredicate(): Map<String, Any> = mapOf(
        "type" to "object",
        "description" to "One comparison request.",
        "required" to listOf("o"),
        "properties" to mapOf(
            "o" to mapOf(
                "type" to "string",
                "enum" to Operator.entries.map { it.name },
                "description" to "The operator. An operator the server does not know is ignored.",
            ),
            "v" to mapOf(
                "type" to "array",
                "items" to emptyMap<String, Any>(),
                "description" to "The operands: one for a comparison, two for `BETWEEN`, any number for `IN`, none for `IS_NULL` and `IS_NOT_NULL`.",
            ),
        ),
    )

    private fun criterion(): Map<String, Any> = mapOf(
        "description" to "The filter on one field: an object with the comparison requests, or a bare value meaning equality. " +
            "Requests accumulate: `{\"p\":[{\"o\":\"GE\",\"v\":[10.5]},{\"o\":\"LE\",\"v\":[20.5]}]}` is a range.",
        "oneOf" to listOf(
            mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "or" to mapOf("type" to "boolean", "description" to "`true` combines the requests of this field with `OR` (default `AND`)."),
                    "p" to mapOf("type" to "array", "items" to schemaRef("CriterionPredicate"), "description" to "The comparison requests."),
                ),
            ),
            mapOf("type" to "string"), mapOf("type" to "number"), mapOf("type" to "boolean"),
        ),
        // as propriedades também ficam no nível de cima, para quem lê o esquema sem resolver o oneOf
        "properties" to mapOf(
            "or" to mapOf("type" to "boolean"),
            "p" to mapOf("type" to "array", "items" to schemaRef("CriterionPredicate")),
        ),
    )

    /** Os campos saem de `criterions()` e as ordenações do `OrderBy` da entidade. */
    private fun criteriaSchema(entity: Entity): Map<String, Any> {
        val properties = LinkedHashMap<String, Any>()
        for (criterion in entity.criteria.criterions()) {
            properties[criterion.name] = mapOf(
                "allOf" to listOf(schemaRef("Criterion")),
                "description" to family(criterion),
            )
        }
        properties["orderBy"] = mapOf(
            "type" to "string",
            "enum" to entity.orderings.map { it.name },
            "description" to "Named ordering. An unknown value is refused with 400.",
        )
        return mapOf(
            "type" to "object",
            "description" to "Filter fields of ${entity.label}. Fields combine with `AND`; an absent field does not filter.",
            "properties" to properties,
        )
    }

    /** A família do critério, lida da classe — a mesma distinção que o compilador impõe a quem monta o filtro. */
    private fun family(criterion: Criterion<*, *>): String = when (criterion) {
        is TextCriterion<*> -> "Text field: equality, comparison, `LIKE`/`ILIKE` (wildcards are part of the value), `IN`, null tests."
        is ComparableCriterion<*, *> -> "Ordered field: equality, `GT`/`GE`/`LT`/`LE`, `BETWEEN`, `IN`, null tests."
        else -> "Identity field: equality, `NE`, `IN`, null tests."
    }

    private fun projectedCollection(): Map<String, Any> = mapOf(
        "type" to "object",
        "description" to "A to-many field in a projection, with what to bring from the collection.",
        "required" to listOf("shape"),
        "properties" to mapOf(
            "shape" to mapOf("type" to "object", "description" to "The projection of each element."),
            "where" to mapOf("type" to "object", "description" to "Criteria of the element type, `orderBy` included."),
            "limit" to mapOf("type" to "integer", "format" to "int32", "description" to "At most this many elements."),
            "offset" to mapOf("type" to "integer", "format" to "int32", "description" to "Skip this many elements."),
        ),
    )

    private fun fetchRequest(entity: Entity, paged: Boolean): Map<String, Any> {
        val slice: Map<String, Any> = if (paged) {
            mapOf(
                "page" to mapOf("type" to "integer", "format" to "int32", "description" to "Zero-based page."),
                "pageSize" to mapOf("type" to "integer", "format" to "int32", "description" to "Rows per page."),
            )
        } else {
            mapOf(
                "offset" to mapOf("type" to "integer", "format" to "int32", "description" to "Rows to skip; `0` skips none."),
                "limit" to mapOf("type" to "integer", "format" to "int32", "description" to "Maximum rows; `0` means no limit."),
            )
        }
        return mapOf(
            "description" to "The criteria fields of ${entity.label}, plus what to bring and which rows.",
            "allOf" to listOf(
                schemaRef("${entity.name}Criteria"),
                mapOf("type" to "object", "properties" to (mapOf("projection" to schemaRef(entity.name)) + slice)),
            ),
        )
    }

    // :: Rotas

    private fun entityPaths(e: Entity): Map<String, Any> {
        val base = "/api/repo/${e.route}"
        val tag = listOf(e.name)
        return linkedMapOf(
            "$base/insert" to post(tag, "Insert a ${e.label}", "Transactional. The id is generated when absent.", e.name, "InsertResponse", write = true),
            "$base/update" to post(
                tag, "Update a ${e.label}",
                "Transactional. Partial: **the keys present in the body** are the fields to update — including to `null`. The id is required.",
                e.name, "SuccessResponse", write = true,
            ),
            "$base/delete" to post(
                tag, "Delete by criteria",
                "Transactional. At least one criteria field is required: an empty criteria is refused with 400.",
                "${e.name}Criteria", "CountResponse", write = true,
            ),
            "$base/count" to post(tag, "Count by criteria", "", "${e.name}Criteria", "CountResponse"),
            "$base/fetch" to post(tag, "Fetch by criteria", "", "${e.name}FetchRequest", "${e.name}List"),
            "$base/fetch-page" to post(tag, "Fetch one page", "", "${e.name}FetchPageRequest", "${e.name}Page"),
            "$base/fetch-by-id" to mapOf(
                "post" to operation(tag, "Fetch by key, with a projection", "") + mapOf(
                    "requestBody" to body(
                        mapOf(
                            "type" to "object", "required" to listOf("id"),
                            "properties" to mapOf("id" to long("The key."), "projection" to schemaRef(e.name)),
                        )
                    ),
                    "responses" to responses(e.name) + notFound(),
                ),
            ),
            "$base/{id}" to mapOf(
                "get" to operation(tag, "Fetch by key, with the default projection", "") + mapOf(
                    "parameters" to listOf(idParameter()),
                    "responses" to responses(e.name) + notFound(),
                ),
            ),
        )
    }

    private fun imagePaths(): Map<String, Any> = mapOf(
        "/api/repo/product/{id}/image" to mapOf(
            "get" to operation(listOf("Product"), "Read the product image", "Public: no authentication required.") + mapOf(
                "security" to listOf(emptyMap<String, Any>()),
                "parameters" to listOf(
                    idParameter(),
                    mapOf(
                        "name" to "size", "in" to "query", "required" to false,
                        "schema" to mapOf("type" to "integer", "minimum" to 16, "maximum" to 1024),
                        "description" to "Largest side, in pixels. The image is scaled down, never up.",
                    ),
                ),
                "responses" to mapOf(
                    "200" to mapOf("description" to "The image.", "content" to mapOf("image/png" to mapOf("schema" to mapOf("type" to "string", "format" to "binary")))),
                    "204" to mapOf("description" to "The product has no image."),
                ),
            ),
            "put" to operation(listOf("Product"), "Replace the product image", "Transactional.") + mapOf(
                "parameters" to listOf(idParameter(), paramRef("TxId"), paramRef("ClientId")),
                "requestBody" to mapOf(
                    "required" to true,
                    "content" to mapOf("application/octet-stream" to mapOf("schema" to mapOf("type" to "string", "format" to "binary"))),
                ),
                "responses" to responses("SuccessResponse") + refusals(write = true),
            ),
        ),
    )

    private fun transactionPaths(): Map<String, Any> {
        val tag = listOf("Transaction")
        fun outcome(summary: String, description: String) = mapOf(
            "post" to operation(tag, summary, description) + mapOf(
                "parameters" to listOf(paramRef("TxId"), paramRef("ClientId")),
                "responses" to responses("TransactionStatus") + mapOf(
                    "403" to error("`X-Tx-Id` is missing, or the transaction belongs to someone else."),
                    "409" to error("The transaction already had the opposite outcome, or is in use by another request."),
                ),
            ),
        )
        return linkedMapOf(
            "/api/tx/begin" to mapOf(
                "post" to operation(
                    tag, "Open a remote transaction",
                    "The owner is the authenticated user or, without security, the `X-Client-Id`. While it is open, a write " +
                        "of the same owner **without** `X-Tx-Id` is refused with 409. An idle transaction is rolled back by the server.",
                ) + mapOf(
                    "parameters" to listOf(paramRef("ClientId")),
                    "responses" to responses("TransactionStarted") + mapOf("429" to error("Too many open transactions, in total or for this owner.")),
                ),
            ),
            "/api/tx/commit" to outcome("Commit", "Idempotent: committing again answers the same."),
            "/api/tx/rollback" to outcome("Roll back", "Idempotent: rolling back again answers the same."),
            "/api/tx/status" to mapOf(
                "get" to operation(tag, "State of a transaction", "Tells what happened when the answer of a commit or rollback was lost.") + mapOf(
                    "parameters" to listOf(paramRef("TxId"), paramRef("ClientId")),
                    "responses" to responses("TransactionStatus") + mapOf("403" to error("`X-Tx-Id` is missing, or the transaction belongs to someone else.")),
                ),
            ),
        )
    }

    private fun authPaths(): Map<String, Any> {
        val tag = listOf("Auth")
        val open = mapOf("security" to listOf(emptyMap<String, Any>()))
        fun credentials(vararg fields: Pair<String, Any>) =
            body(mapOf("type" to "object", "required" to fields.map { it.first }, "properties" to mapOf(*fields)))
        return linkedMapOf(
            "/api/auth/challenge" to mapOf(
                "get" to operation(tag, "Get a login nonce", "") + open + mapOf(
                    "responses" to mapOf(
                        "200" to okResponse(
                            mapOf(
                                "type" to "object",
                                "properties" to mapOf(
                                    "nonce" to string("Single use."),
                                    "expiresAt" to string("When the nonce stops being accepted.") + mapOf("format" to "date-time"),
                                ),
                            )
                        ),
                    ),
                ),
            ),
            "/api/auth/login" to mapOf(
                "post" to operation(tag, "Log in", "The password never travels: the client proves it knows the password digest.") + open + mapOf(
                    "requestBody" to credentials(
                        "userName" to string("Login."),
                        "digest" to string("Hex HMAC-SHA256 of `userName + nonce`, keyed by the password digest."),
                        "nonce" to string("The nonce from `/api/auth/challenge`."),
                    ),
                    "responses" to responses("AuthTokens") + mapOf("401" to error("Invalid credentials.")),
                ),
            ),
            "/api/auth/refresh" to mapOf(
                "post" to operation(tag, "Renew the session", "") + open + mapOf(
                    "requestBody" to credentials("refreshToken" to string("The refresh token of the session.")),
                    "responses" to responses("AuthTokens") + mapOf("401" to error("Invalid or expired refresh token.")),
                ),
            ),
            "/api/auth/logout" to mapOf(
                "post" to operation(tag, "End the session", "") + mapOf(
                    "requestBody" to credentials("refreshToken" to string("The refresh token of the session.")),
                    "responses" to responses("SuccessResponse"),
                ),
            ),
        )
    }

    // :: Peças

    private fun operation(tags: List<String>, summary: String, description: String): Map<String, Any> =
        linkedMapOf<String, Any>("tags" to tags, "summary" to summary).apply { if (description.isNotEmpty()) put("description", description) }

    private fun post(tags: List<String>, summary: String, description: String, request: String, response: String, write: Boolean = false): Map<String, Any> {
        val op = LinkedHashMap(operation(tags, summary, description))
        if (write) {
            op["parameters"] = listOf(paramRef("TxId"), paramRef("ClientId"))
        }
        op["requestBody"] = body(schemaRef(request))
        op["responses"] = responses(response) + refusals(write)
        return mapOf("post" to op)
    }

    private fun body(schema: Map<String, Any>): Map<String, Any> =
        mapOf("required" to true, "content" to mapOf("application/json" to mapOf("schema" to schema)))

    private fun okResponse(schema: Map<String, Any>): Map<String, Any> =
        mapOf("description" to "OK", "content" to mapOf("application/json" to mapOf("schema" to schema)))

    private fun responses(schema: String): Map<String, Any> = mapOf("200" to okResponse(schemaRef(schema)))

    private fun error(description: String): Map<String, Any> =
        mapOf("description" to description, "content" to mapOf("application/json" to mapOf("schema" to schemaRef("Error"))))

    private fun notFound(): Map<String, Any> = mapOf("404" to error("No row with this key (or it is outside the caller's scope)."))

    private fun refusals(write: Boolean): Map<String, Any> {
        val refusals = linkedMapOf<String, Any>(
            "400" to error("Invalid request: unknown `orderBy`, delete with empty criteria, update without the id…"),
            "401" to error("Missing or invalid token (security on)."),
            "403" to error("The caller lacks the permission, or the row is outside their scope."),
        )
        if (write) {
            refusals["409"] = error("The caller has a remote transaction open and the write came without `X-Tx-Id`; or the transaction is in use.")
        }
        return refusals
    }

    private fun idParameter(): Map<String, Any> =
        mapOf("name" to "id", "in" to "path", "required" to true, "schema" to mapOf("type" to "integer", "format" to "int64"))

    private fun schemaRef(name: String): Map<String, Any> = mapOf("\$ref" to "#/components/schemas/$name")

    private fun paramRef(name: String): Map<String, Any> = mapOf("\$ref" to "#/components/parameters/$name")

    private fun ref(name: String, description: String): Map<String, Any> =
        mapOf("allOf" to listOf(schemaRef(name)), "description" to description)

    private fun obj(description: String, vararg properties: Pair<String, Any>): Map<String, Any> =
        mapOf("type" to "object", "description" to description, "properties" to linkedMapOf(*properties))

    private fun string(description: String): Map<String, Any> = mapOf("type" to "string", "description" to description)

    private fun boolean(description: String): Map<String, Any> = mapOf("type" to "boolean", "description" to description)

    private fun number(description: String): Map<String, Any> = mapOf("type" to "number", "format" to "double", "description" to description)

    private fun long(description: String): Map<String, Any> = mapOf("type" to "integer", "format" to "int64", "description" to description)
}
