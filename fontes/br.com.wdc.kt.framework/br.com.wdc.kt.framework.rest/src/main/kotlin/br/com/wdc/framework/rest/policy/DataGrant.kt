package br.com.wdc.framework.rest.policy

import br.com.wdc.framework.domain.criteria.Criteria
import kotlin.reflect.KProperty1

/**
 * What one role may do with one entity, over one set of rows: which fields it reads as they are, which it reads
 * masked, which it may set when creating and when updating, and whether it may delete.
 *
 * The fields are named by their property references — the same ones the [EntityDescriptor] declares —, so a renamed
 * field breaks the policy at compile time. A role that reaches different rows with different fields (its own user in
 * full, the others partially) has one grant for each.
 *
 * ```
 * grant("CUSTOMER") {
 *     rows(OwnUser)
 *     read(User::userName, User::name)
 *     update(User::name)
 * }
 * ```
 *
 * The key is not declared: whoever reads a row reads its key, and it is never written — it identifies the row. A
 * relation or a collection is one field here (`read(Order::items)`): what is read inside it is up to the policy of the
 * related entity.
 */
class DataGrant<E : Any, C : Criteria> private constructor(
    /** The rows the grant reaches. */
    val rows: RowScope<E, C>,
    /** The fields read as they are. They are also the ones the grant may filter and sort by. */
    val read: Set<String>,
    /** The fields read through a mask. */
    val masked: Map<String, Mask>,
    val create: Set<String>,
    val update: Set<String>,
    val delete: Boolean,
) {

    /** Whether the grant reads the entity at all. */
    val reads: Boolean get() = read.isNotEmpty() || masked.isNotEmpty()

    /** Whether it reads the field, as it is or masked. */
    internal fun sees(field: String): Boolean = field in read || field in masked

    override fun toString(): String = "DataGrant(rows=$rows, read=$read, masked=${masked.keys}, create=$create, update=$update, delete=$delete)"

    /**
     * Declares a grant. It rejects what would contradict the policy: masking a field that is not textual, or the key;
     * reading the same field both ways; writing a field that is read masked; reading a write-only field.
     */
    class Builder<E : Any, C : Criteria> internal constructor(private val entity: EntityDescriptor<E, C>) {
        private var rows: RowScope<E, C> = RowScope.all()
        private val read = LinkedHashSet<String>()
        private val masked = LinkedHashMap<String, Mask>()
        private val create = LinkedHashSet<String>()
        private val update = LinkedHashSet<String>()
        private var delete = false

        /** The rows the grant reaches; every row when not declared. */
        fun rows(scope: RowScope<E, C>) {
            rows = scope
        }

        /** Fields read as they are — and usable in filters and orderings. */
        fun read(vararg fields: KProperty1<E, *>) {
            read.addAll(readable(fields))
        }

        /** Fields read through [mask]; only textual fields, and never the key. */
        fun mask(mask: Mask, vararg fields: KProperty1<E, *>) {
            for (fieldName in readable(fields)) {
                val field = entity.field(fieldName)
                check(field !== entity.key) { "${entity.name}.$fieldName is the key, always read as it is, and cannot be masked" }
                check(field.textual) { "${entity.name}.$fieldName is not textual and cannot be masked" }
                masked[fieldName] = mask
            }
        }

        /** Fields that may be set when creating a row. */
        fun create(vararg fields: KProperty1<E, *>) {
            create.addAll(writable(fields))
        }

        /** Fields that may be changed in a reachable row. */
        fun update(vararg fields: KProperty1<E, *>) {
            update.addAll(writable(fields))
        }

        /** The reachable rows may be deleted. */
        fun delete() {
            delete = true
        }

        private fun readable(fields: Array<out KProperty1<E, *>>): List<String> = entity.namesOf(fields).onEach {
            check(!entity.field(it).writeOnly) { "${entity.name}.$it is write-only and cannot be read" }
        }

        private fun writable(fields: Array<out KProperty1<E, *>>): List<String> = entity.namesOf(fields).onEach {
            check(entity.field(it) !== entity.key) { "${entity.name}.$it is the key, which identifies the row and is not written" }
        }

        internal fun build(): DataGrant<E, C> {
            for (fieldName in masked.keys) {
                check(fieldName !in read) { "${entity.name}.$fieldName is declared both read and masked" }
                check(fieldName !in create && fieldName !in update) {
                    "${entity.name}.$fieldName is masked and cannot be written by the same grant"
                }
            }
            return DataGrant(rows, read.toSet(), masked.toMap(), create.toSet(), update.toSet(), delete)
        }
    }

    internal companion object {
        /** With security off: every row, every field but the write-only ones read; every write allowed. */
        fun <E : Any, C : Criteria> unrestricted(entity: EntityDescriptor<E, C>): DataGrant<E, C> {
            val all = entity.fieldNames.toSet()
            val readable = entity.fieldNames.filterTo(LinkedHashSet()) { !entity.field(it).writeOnly }
            return DataGrant(RowScope.all(), readable, emptyMap(), all, all, true)
        }
    }
}
