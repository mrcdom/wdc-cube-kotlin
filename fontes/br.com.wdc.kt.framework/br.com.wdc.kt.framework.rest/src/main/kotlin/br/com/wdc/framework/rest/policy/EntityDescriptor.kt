package br.com.wdc.framework.rest.policy

import br.com.wdc.framework.domain.criteria.Criteria
import br.com.wdc.framework.domain.projection.ProjectionList
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1

/**
 * What a [DataPolicy] needs to know about an entity: its fields, by the names they have on the wire, its relations to
 * other entities, and how its criteria refer to them.
 *
 * A field is declared by its property reference — `value(Order::status, ProjectionValues.str)` —, so a renamed
 * property breaks the policy at compile time, and the grants name fields the same way (`read(Order::status)`). The
 * wire name defaults to the property name; pass `name` when the codec writes another one.
 *
 * A field is a **value**, a **relation** to one row of another entity (`order.customer`), or a **collection** of rows
 * of another entity (`order.items`). A relation or collection names the policy of the other entity: what is read
 * inside it goes through that policy, so an entity has one set of rules wherever it appears. The policy is given as a
 * function because entities refer to each other — the order to its items, the item to its order.
 *
 * It also says which field each criteria field filters ([Builder.criterion]; by default, the field of the same name)
 * and which fields each ordering sorts by ([Builder.orderings]), because filtering or sorting by a field reveals its
 * values as much as reading it does. Construction checks that every criteria field resolves to an entity field, so a
 * criteria field added without a mapping fails when the policy is loaded, not when a request uses it.
 *
 * ```
 * val ORDER = EntityDescriptor("order", ::Order, ::OrderCriteria) {
 *     projection(OrderCriteria::projection, OrderCriteria::withProjection)
 *     anyOf(OrderCriteria::anyOf, OrderCriteria::withAnyOf)
 *     key(Order::id, ProjectionValues.i64)
 *     value(Order::status, ProjectionValues.str)
 *     relation(Order::customer) { CustomerPolicy.POLICY }
 *     collection(Order::items, inverse = OrderItem::order) { OrderItemPolicy.POLICY }
 *     criterion("orderId", Order::id)
 *     criterion("customerId", Order::customer)
 *     orderings { c -> if (c.orderBy == OrderBy.BY_STATUS) setOf(Order::status) else emptySet() }
 * }
 * ```
 *
 * @param E the entity — a mutable bean where a non-null field, in a projection, means "bring this field"
 * @param C its criteria
 * @property name the entity name used in the messages, as in the REST paths (`user`, `purchase-item`)
 */
class EntityDescriptor<E : Any, C : Criteria>(
    val name: String,
    private val entityFactory: () -> E,
    private val criteriaFactory: () -> C,
    declare: Builder<E, C>.() -> Unit,
) {

    /** What a field holds. */
    internal enum class Kind { VALUE, RELATION, COLLECTION }

    /** A field of the entity: its wire name, how to read and write it, and how it is marked in a projection. */
    class Field<E : Any> internal constructor(
        /** The name on the wire. */
        val name: String,
        internal val kind: Kind,
        internal val property: KProperty1<E, *>,
        private val getter: (E) -> Any?,
        private val setter: (E, Any?) -> Unit,
        private val marker: Any?,
        private val target: (() -> DataPolicy<*, *>)?,
        private val inverseProperty: KProperty1<*, *>?,
        /** Never read through the API, by anyone: outside every read grant, and out of the default shape. */
        val writeOnly: Boolean,
    ) {
        /** Whether the field is textual — only a textual field can be masked. */
        val textual: Boolean get() = kind == Kind.VALUE && marker is String

        @Suppress("UNCHECKED_CAST")
        internal fun targetPolicy(): DataPolicy<Any, Criteria> =
            (target ?: error("$name is not a relation")).invoke() as DataPolicy<Any, Criteria>

        internal fun targetEntity(): EntityDescriptor<Any, Criteria> = targetPolicy().entity

        /** For a collection: the field of each child that points back to the owner. */
        internal fun inverse(): Field<Any> = targetEntity().field(inverseProperty ?: error("$name is not a collection"))

        internal fun get(entity: E): Any? = getter(entity)
        internal fun isSet(entity: E): Boolean = getter(entity) != null
        internal fun clear(entity: E) = setter(entity, null)
        internal fun setRaw(entity: E, value: Any?) = setter(entity, value)
        internal fun copy(from: E, to: E) = setter(to, getter(from))

        /** Marks the field in a projection: the marker of a value; the key of a relation, or of the collection's rows. */
        internal fun mark(projection: E) = when (kind) {
            Kind.VALUE -> setter(projection, marker)
            Kind.RELATION -> setter(projection, targetEntity().projectionOf(emptySet()))
            Kind.COLLECTION -> setter(projection, ProjectionList(targetEntity().projectionOf(emptySet()), null))
        }

        internal fun mask(entity: E, mask: Mask) {
            (getter(entity) as? String)?.let { setter(entity, mask.apply(it)) }
        }

        override fun toString(): String = name
    }

    private val projectionGetter: (C) -> E?
    private val projectionSetter: (C, E?) -> Unit
    private val anyOfGetter: (C) -> List<C>
    private val anyOfSetter: (C, List<C>) -> Unit
    internal val key: Field<E>
    private val fields: Map<String, Field<E>>
    private val criterionFields: Map<String, String>
    private val orderingFields: (C) -> Set<KProperty1<E, *>>
    private val declaredDefaultShape: (() -> E)?

    init {
        val b = Builder<E, C>(name).apply(declare)
        projectionGetter = b.projectionGetter ?: error("$name: projection(...) is required")
        projectionSetter = b.projectionSetter!!
        anyOfGetter = b.anyOfGetter ?: error("$name: anyOf(...) is required")
        anyOfSetter = b.anyOfSetter!!
        key = b.key ?: error("$name: key(...) is required")
        fields = LinkedHashMap(b.fields)
        criterionFields = HashMap(b.criterionFields)
        orderingFields = b.orderingFields
        declaredDefaultShape = b.defaultShape
        val unknown = criteriaFactory().criterions()
            .map { it.name }
            .filter { (criterionFields[it] ?: it) !in fields }
        check(unknown.isEmpty()) { "$name: criteria fields with no entity field: $unknown" }
    }

    /** The key field's name. The key identifies the row, so every grant that reads the entity reads it too. */
    val keyName: String get() = key.name

    /** The fields other than the key, in declaration order. */
    val fieldNames: List<String> get() = fields.keys.filter { it != key.name }

    internal fun field(fieldName: String): Field<E> =
        fields[fieldName] ?: throw IllegalArgumentException("$name has no field $fieldName")

    internal fun field(property: KProperty1<*, *>): Field<E> =
        fields.values.firstOrNull { it.property == property }
            ?: throw IllegalArgumentException("$name has no field for the property ${property.name}")

    /** The wire names of [properties], for the grants. */
    internal fun namesOf(properties: Array<out KProperty1<E, *>>): List<String> = properties.map { field(it).name }

    /**
     * The fields, other than the key, that are set in [entity] — for a projection, the fields it asks for. A relation
     * or a collection counts when it is present, whatever it carries inside.
     */
    internal fun fieldsSetIn(entity: E?): LinkedHashSet<String> {
        val names = LinkedHashSet<String>()
        if (entity == null) return names
        for (field in fields.values) {
            if (field !== key && field.isSet(entity)) names.add(field.name)
        }
        return names
    }

    /** A projection with the key and the given fields — relations and collections by key only. */
    internal fun projectionOf(fieldNames: Set<String>): E {
        val projection = entityFactory()
        key.mark(projection)
        for (fieldName in fieldNames) field(fieldName).mark(projection)
        return projection
    }

    /**
     * A projection that asks for what [row] carries — the fields set in it, into its relations and collections. A row
     * already met on the way (a child pointing back to its owner) is asked by key only.
     */
    internal fun projectionMirroring(row: E, seen: MutableSet<Any> = identitySet()): E {
        seen.add(row)
        val projection = entityFactory()
        key.mark(projection)
        for (fieldName in fieldsSetIn(row)) {
            val field = field(fieldName)
            val value = field.get(row)
            when (field.kind) {
                Kind.VALUE -> field.mark(projection)
                Kind.RELATION ->
                    if (value == null || value in seen) field.mark(projection)
                    else field.setRaw(projection, field.targetEntity().projectionMirroring(value, seen))
                Kind.COLLECTION -> {
                    val first = (value as? List<*>)?.firstOrNull()
                    if (first == null) field.mark(projection)
                    else field.setRaw(projection, ProjectionList(field.targetEntity().projectionMirroring(first, seen), null))
                }
            }
        }
        return projection
    }

    /**
     * Adds to [into] what [from] asks for, going into the relations. It is how the fields a row scope reads join the
     * projection of a read. A collection cannot be merged: a row scope does not reach through one.
     */
    internal fun merge(into: E, from: E?) {
        if (from == null) return
        for (fieldName in fieldsSetIn(from)) {
            val field = field(fieldName)
            when (field.kind) {
                Kind.VALUE -> field.mark(into)
                Kind.RELATION -> {
                    if (!field.isSet(into)) field.mark(into)
                    field.targetEntity().merge(field.get(into)!!, field.get(from))
                }
                Kind.COLLECTION -> throw IllegalStateException(
                    "$name.$fieldName is a collection, and a row scope cannot read through it",
                )
            }
        }
    }

    /** Clears every field of [row] but the key — a related row read by its key only. */
    internal fun keepKeyOnly(row: E) {
        for (field in fields.values) if (field !== key) field.clear(row)
    }

    internal fun newEntity(): E = entityFactory()

    internal fun newCriteria(): C = criteriaFactory()

    /**
     * What a read without a projection gets, before it is trimmed to what the requester may read: the shape the
     * entity declares, or else every field but the collections and the write-only ones, relations by key.
     */
    internal fun defaultShape(): E = declaredDefaultShape?.invoke()
        ?: projectionOf(fields.values.filter { it !== key && it.kind != Kind.COLLECTION && !it.writeOnly }.mapTo(LinkedHashSet()) { it.name })

    internal fun projection(criteria: C): E? = projectionGetter(criteria)

    internal fun setProjection(criteria: C, projection: E?) = projectionSetter(criteria, projection)

    internal fun anyOf(criteria: C): List<C> = anyOfGetter(criteria)

    internal fun setAnyOf(criteria: C, groups: List<C>) = anyOfSetter(criteria, groups)

    /**
     * The fields, other than the key, that the criteria filters or sorts by — its own fields, those of its alternative
     * groups, and those of its ordering.
     */
    internal fun filteredFields(criteria: C): LinkedHashSet<String> {
        val names = LinkedHashSet<String>()
        collectFiltered(criteria, names)
        orderingFields(criteria).mapTo(names) { field(it).name }
        names.remove(key.name)
        return names
    }

    private fun collectFiltered(criteria: C, names: MutableSet<String>) {
        for (criterion in criteria.criterions()) {
            if (criterion.isSet()) names.add(criterionFields[criterion.name] ?: criterion.name)
        }
        for (group in anyOf(criteria)) collectFiltered(group, names)
    }

    override fun toString(): String = name

    /** The declaration of an [EntityDescriptor]: `projection`, `anyOf` and `key` are required. */
    class Builder<E : Any, C : Criteria> internal constructor(private val entityName: String) {
        internal var projectionGetter: ((C) -> E?)? = null
        internal var projectionSetter: ((C, E?) -> Unit)? = null
        internal var anyOfGetter: ((C) -> List<C>)? = null
        internal var anyOfSetter: ((C, List<C>) -> Unit)? = null
        internal var key: Field<E>? = null
        internal val fields = LinkedHashMap<String, Field<E>>()
        internal val criterionFields = HashMap<String, String>()
        internal var orderingFields: (C) -> Set<KProperty1<E, *>> = { emptySet() }
        internal var defaultShape: (() -> E)? = null

        /** How the criteria carries its projection. */
        fun projection(get: (C) -> E?, set: (C, E?) -> Unit) {
            projectionGetter = get
            projectionSetter = set
        }

        /** How the criteria carries its alternative groups (`Criteria.anyOf`); the policy restricts rows with them. */
        fun anyOf(get: (C) -> List<C>, set: (C, List<C>) -> Unit) {
            anyOfGetter = get
            anyOfSetter = set
        }

        /** The key: always read as it is, never written, never masked. */
        fun <T : Any> key(property: KMutableProperty1<E, T?>, marker: T, name: String = property.name) {
            val field = newField(name, Kind.VALUE, property, marker)
            key = field
            fields[name] = field
        }

        /** A value field — [marker] is what marks it in a projection (`ProjectionValues.str`…). */
        fun <T : Any> value(property: KMutableProperty1<E, T?>, marker: T, name: String = property.name) {
            fields[name] = newField(name, Kind.VALUE, property, marker)
        }

        /**
         * A value field that is written but never read through the API — a password: no grant may read or mask it,
         * asking for it is refused even with security off, and it is out of the default shape.
         */
        fun <T : Any> writeOnly(property: KMutableProperty1<E, T?>, marker: T, name: String = property.name) {
            fields[name] = newField(name, Kind.VALUE, property, marker, writeOnly = true)
        }

        /** A relation to one row of another entity, read through that entity's policy. */
        fun <R : Any> relation(property: KMutableProperty1<E, R?>, name: String = property.name, target: () -> DataPolicy<R, *>) {
            fields[name] = newField(name, Kind.RELATION, property, null, target = target)
        }

        /**
         * A collection of rows of another entity, read and created through that entity's policy. The property's list
         * type must accept a [ProjectionList] (a `MutableList`), which a read puts there as the children's projection.
         *
         * @param inverse the relation of each child that points back to the owner — set on the children of a new row
         *   while they are checked, since a child of a new row belongs to it
         */
        fun <R : Any, L : List<R>> collection(
            property: KMutableProperty1<E, L?>,
            inverse: KMutableProperty1<R, E?>,
            name: String = property.name,
            target: () -> DataPolicy<R, *>,
        ) {
            fields[name] = newField(name, Kind.COLLECTION, property, null, target = target, inverse = inverse)
        }

        /** A criteria field whose name differs from the entity field it filters (`userId` → `User::id`). */
        fun criterion(criterionName: String, field: KProperty1<E, *>) {
            criterionFields[criterionName] = fields.values.firstOrNull { it.property == field }?.name
                ?: throw IllegalStateException("$entityName: declare the field ${field.name} before mapping $criterionName to it")
        }

        /** The fields each ordering of the criteria sorts by; the key (a tie-breaker) need not be named. */
        fun orderings(fields: (C) -> Set<KProperty1<E, *>>) {
            orderingFields = fields
        }

        /**
         * The shape a read without a projection gets — it may go into relations and collections, as a receipt does
         * with its items. A new projection on each call: a read changes the one it is given. Without it, every field
         * but the collections and the write-only ones, relations by key.
         */
        fun defaultShape(shape: () -> E) {
            defaultShape = shape
        }

        @Suppress("UNCHECKED_CAST")
        private fun newField(
            name: String,
            kind: Kind,
            property: KMutableProperty1<E, *>,
            marker: Any?,
            target: (() -> DataPolicy<*, *>)? = null,
            inverse: KProperty1<*, *>? = null,
            writeOnly: Boolean = false,
        ): Field<E> {
            val setter = property as KMutableProperty1<E, Any?>
            return Field(name, kind, property, { property.get(it) }, { e, v -> setter.set(e, v) }, marker, target, inverse, writeOnly)
        }
    }
}

/** A set compared by identity: an entity's `equals` is not what tells two rows apart on the way through a graph. */
internal fun identitySet(): MutableSet<Any> = java.util.Collections.newSetFromMap(java.util.IdentityHashMap())
