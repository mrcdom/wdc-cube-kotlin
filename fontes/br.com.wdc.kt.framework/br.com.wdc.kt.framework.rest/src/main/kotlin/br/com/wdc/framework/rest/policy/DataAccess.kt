package br.com.wdc.framework.rest.policy

import br.com.wdc.framework.domain.criteria.Criteria
import br.com.wdc.framework.domain.exception.AccessDeniedException
import br.com.wdc.framework.domain.exception.InvalidRequestException
import br.com.wdc.framework.domain.exception.NotFoundException
import br.com.wdc.framework.domain.projection.HasCriteria
import br.com.wdc.framework.domain.projection.HasSlice
import br.com.wdc.framework.domain.projection.ProjectionList
import br.com.wdc.framework.rest.policy.EntityDescriptor.Kind
import br.com.wdc.framework.rest.security.RestPrincipal

/**
 * What one requester may do with an entity: the grants of their roles, applied to each request before it reaches the
 * repository, and to each row on its way out. Obtained from [DataPolicy.accessFor].
 *
 * Each method either adapts the request — restricts the rows, completes the projection — or refuses it with an
 * [AccessDeniedException] (403) that names the fields at fault. Refusing is deliberate: a client whose request is
 * quietly trimmed shows blanks without knowing why, and a correct client never asks for what its roles do not allow.
 *
 * - **Reading** ([prepareRead]): every projected field must be seen by some grant; every field the criteria filters or
 *   sorts by must be read as it is by one grant, and only the rows of the grants that do so are returned (as
 *   alternative groups, `Criteria.anyOf`, joined with what the client asked); each row then shows, per field, what the
 *   grants that reach that row allow — as it is, masked, or nothing. What the projection asks **inside** a relation or
 *   a collection goes through the policy of that entity, row by row; the rows of a collection are restricted by it too.
 *   Without a projection, the entity's default shape, trimmed to what may be read.
 * - **Counting** ([restrictCount]): the same rows as reading.
 * - **Creating** ([checkCreate]): one grant must allow every field sent, and reach the new row; a relation is written
 *   by its key only, and each row of a collection is created through the policy of its entity.
 * - **Updating** ([checkUpdate]): one grant must reach the stored row, allow every changed field, and still reach the
 *   row after the change.
 * - **Deleting** ([restrictDelete]): only the rows of grants that delete, filtered by fields they read.
 *
 * Without a requester (security off) the writes are not checked and every row is reached; a read without a projection
 * keeps the repository's default, and the write-only fields are still refused and never returned.
 *
 * @param E the entity
 * @param C its criteria
 */
class DataAccess<E : Any, C : Criteria> internal constructor(
    val entity: EntityDescriptor<E, C>,
    private val requester: RestPrincipal?,
    private val grants: List<DataGrant<E, C>>,
) {

    /** Shapes the rows a read returned: per row, the fields the grants that reach it allow. */
    fun interface ReadPlan<E> {

        /** Shapes [row] in place, and returns it. */
        suspend fun apply(row: E): E

        suspend fun applyAll(rows: List<E>): List<E> {
            for (row in rows) apply(row)
            return rows
        }
    }

    // :: Reading

    /**
     * Adapts a read: checks the projection and the filtered fields, restricts the rows, and sets on the criteria the
     * projection to fetch — what was asked, plus what the row checks need.
     *
     * Without a projection, the read gets the entity's default shape, **trimmed** to what the requester may read
     * instead of refused: the client asked for nothing in particular, so nothing it asked for is forbidden. With security
     * off, it is left without one — the repository's own default —, and only the write-only fields are cleared from
     * the rows.
     *
     * @return the plan that shapes each returned row; apply it before writing the response
     */
    fun prepareRead(criteria: C): ReadPlan<E> {
        val requested = entity.projection(criteria)
        if (requester == null && requested == null) {
            val writeOnly = entity.fieldNames.map(entity::field).filter { it.writeOnly }
            return ReadPlan { row -> row.also { r -> writeOnly.forEach { it.clear(r) } } }
        }
        val shape = if (requested != null) shape(requested, criteria, trim = false) else shape(entity.defaultShape(), criteria, trim = true)
        entity.setProjection(criteria, shape.fetch)
        return ReadPlan { row -> shape.apply(row, identitySet()) }
    }

    /** Restricts a count to the rows a read with the same criteria would return. */
    fun restrictCount(criteria: C) {
        restrictRows(criteria, eligibleToFilter(criteria, readers()))
    }

    /**
     * Shapes a row the server wrote — the entity an insert answers with — as a read of the fields it carries would:
     * what the requester may not read leaves it, nothing is refused. A requester who reads nothing of the entity gets
     * the key only.
     */
    suspend fun readBack(row: E): E {
        if (readsNothing()) {
            entity.keepKeyOnly(row)
            return row
        }
        return shape(entity.projectionMirroring(row), null, trim = true).apply(row, identitySet())
    }

    /**
     * One level of a read: what is asked of this entity, the rows the criteria may reach (when there is a criteria — a
     * relation has none: its row is the one the owner points to), and what to fetch.
     *
     * @param trim whether what the requester may not read is left out (the default shape) instead of refused (a
     *   projection the client sent)
     */
    private fun shape(requested: E, criteria: C?, trim: Boolean): Shape {
        val readers = readers()
        val fields = entity.fieldsSetIn(requested)

        val unseen = fields.filterTo(LinkedHashSet()) { field -> readers.none { it.sees(field) } }
        if (trim) {
            fields.removeAll(unseen)
        } else if (unseen.isNotEmpty()) {
            throw denied("not allowed to read ${entity.name} fields $unseen")
        }

        if (criteria != null) restrictRows(criteria, eligibleToFilter(criteria, readers))

        val fetch = entity.newEntity()
        entity.key.mark(fetch)
        val nested = HashMap<String, DataAccess<*, *>.Shape>()
        val unreadable = LinkedHashSet<String>()
        for (fieldName in fields) {
            val field = entity.field(fieldName)
            val inside = field.get(requested)
            when (field.kind) {
                Kind.VALUE -> field.mark(fetch)
                Kind.RELATION -> {
                    val access = field.targetPolicy().accessFor(requester)
                    if (inside != null && field.targetEntity().fieldsSetIn(inside).isNotEmpty() && !(trim && access.readsNothing())) {
                        val shape = access.shape(inside, null, trim)
                        field.setRaw(fetch, shape.fetch)
                        nested[fieldName] = shape
                    } else {
                        field.mark(fetch)
                    }
                }
                Kind.COLLECTION -> {
                    val access = field.targetPolicy().accessFor(requester)
                    val list = inside as? List<*>
                    val first = list?.firstOrNull()
                    if (trim && access.readsNothing()) {
                        unreadable.add(fieldName)
                    } else if (first != null) {
                        val childCriteria = (list as? HasCriteria)?.criteria as? Criteria ?: field.targetEntity().newCriteria()
                        val shape = access.shape(first, childCriteria, trim)
                        val slice = list as? HasSlice
                        field.setRaw(fetch, ProjectionList(shape.fetch, childCriteria, slice?.limit, slice?.offset))
                        nested[fieldName] = shape
                    } else {
                        field.mark(fetch)
                    }
                }
            }
        }
        fields.removeAll(unreadable)
        for (grant in readers) entity.merge(fetch, grant.rows.projection())
        return Shape(fields, readers, nested, fetch)
    }

    /** Whether no grant reads this entity — then, in a default shape, a relation to it goes by key, a collection out. */
    private fun readsNothing(): Boolean = grants.none { it.reads }

    /** A planned level of a read, and how it shapes each returned row. */
    private inner class Shape(
        val fields: Set<String>,
        val readers: List<DataGrant<E, C>>,
        val nested: Map<String, DataAccess<*, *>.Shape>,
        val fetch: E,
    ) {

        /**
         * The field values of a row: as they are, masked, or cleared, by the grants that reach it. A row already being
         * shaped higher up (a child pointing back to its owner) is left to that level.
         */
        suspend fun apply(row: E, shaping: MutableSet<Any>): E {
            if (!shaping.add(row)) return row
            try {
                val reaching = readers.filter { covers(it, row) }
                for (fieldName in entity.fieldNames) {
                    val field = entity.field(fieldName)
                    if (fieldName !in fields) {
                        field.clear(row)
                        continue
                    }
                    val readAsItIs = reaching.any { fieldName in it.read }
                    if (field.kind != Kind.VALUE) {
                        if (readAsItIs) shapeRelated(fieldName, field, row, shaping) else field.clear(row)
                        continue
                    }
                    if (readAsItIs) continue
                    val mask = reaching.firstNotNullOfOrNull { it.masked[fieldName] }
                    if (mask != null) field.mask(row, mask) else field.clear(row)
                }
                return row
            } finally {
                shaping.remove(row)
            }
        }

        /** The related row (or rows): through the related entity's plan, or reduced to the key when only it was asked. */
        private suspend fun shapeRelated(fieldName: String, field: EntityDescriptor.Field<E>, row: E, shaping: MutableSet<Any>) {
            val inside = nested[fieldName]
            when (val value = field.get(row)) {
                null -> Unit
                is List<*> -> for (child in value) if (child != null) shapeOne(inside, field, child, shaping)
                else -> shapeOne(inside, field, value, shaping)
            }
        }

        @Suppress("UNCHECKED_CAST")
        private suspend fun shapeOne(inside: DataAccess<*, *>.Shape?, field: EntityDescriptor.Field<E>, related: Any, shaping: MutableSet<Any>) {
            if (related in shaping) return
            if (inside != null) {
                (inside as DataAccess<Any, Criteria>.Shape).apply(related, shaping)
            } else {
                field.targetEntity().keepKeyOnly(related)
            }
        }
    }

    // :: Writing

    /**
     * Checks a new row: one grant must allow every field set in it and reach it. A relation is written by its key
     * only; each row of a collection — the items of a new order — is checked as a new row of its entity, belonging to
     * this one.
     */
    suspend fun checkCreate(row: E) {
        if (requester != null) checkCreate(row, null)
    }

    /** @param owner the relation that points to the row this one is created inside, set by the owner's check */
    private suspend fun checkCreate(row: E, owner: String?) {
        val fields = entity.fieldsSetIn(row)
        val creators = grants.filter { it.create.isNotEmpty() }
        if (creators.isEmpty()) throw denied("not allowed to create ${entity.name}")
        requireRelationsByKey(row, fields, owner)
        val allowed = creators.any { it.create.containsAll(fields) && covers(it, row) }
        if (!allowed) throw denied("not allowed to create ${entity.name} with ${notIn(fields, creators) { it.create }}")
        for (fieldName in fields) {
            val field = entity.field(fieldName)
            if (field.kind == Kind.COLLECTION) checkCreateChildren(field, row)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun checkCreateChildren(field: EntityDescriptor.Field<E>, row: E) {
        val inverse = field.inverse()
        val access = field.targetPolicy().accessFor(requester)
        for (child in field.get(row) as List<*>) {
            if (child == null) continue
            // the child belongs to the new row while it is checked; what the codec put there is given back after
            val previous = inverse.get(child)
            inverse.setRaw(child, row)
            try {
                access.checkCreate(child, inverse.name)
            } finally {
                inverse.setRaw(child, previous)
            }
        }
    }

    /** A relation sent with more than its key would look like a write of the related row, which it is not. */
    private fun requireRelationsByKey(row: E, fields: Set<String>, owner: String?) {
        for (fieldName in fields) {
            val field = entity.field(fieldName)
            if (field.kind == Kind.RELATION && fieldName != owner && field.targetEntity().fieldsSetIn(field.get(row)).isNotEmpty()) {
                throw denied("${entity.name}.$fieldName is written by its key only")
            }
        }
    }

    /**
     * Checks an update: one grant must reach the stored row, allow every changed field, and still reach the row once
     * changed — a grant over one's own rows cannot hand a row to someone else.
     *
     * A stored row that no updating grant reaches is answered as [NotFoundException] (404) when the requester cannot
     * read it either — the same as a row that does not exist: a 403 would tell the row is there, which a read hides —,
     * and as [AccessDeniedException] (403) when they can read it.
     *
     * @param row the new values, with the key
     * @param changes a projection of the changed fields
     * @param loader fetches the stored row by the key of [row], with the given projection; `null` when it does not
     *   exist — then there is nothing to check, and the update changes nothing
     */
    suspend fun checkUpdate(row: E, changes: E, loader: suspend (row: E, projection: E) -> E?) {
        if (requester == null) return
        val fields = entity.fieldsSetIn(changes)
        val updaters = grants.filter { it.update.isNotEmpty() }
        if (updaters.isEmpty()) throw denied("not allowed to update ${entity.name}")
        if (entity.key.get(row) == null) throw InvalidRequestException("update of ${entity.name} without the key")
        if (fields.isEmpty()) return
        requireRelationsByKey(row, fields, null)

        val readers = grants.filter { it.reads }
        val scope = entity.projectionOf(emptySet())
        for (grant in updaters + readers) entity.merge(scope, grant.rows.projection())
        val stored = loader(row, scope) ?: return

        val reaching = updaters.filter { covers(it, stored) }
        if (reaching.isEmpty()) {
            if (readers.none { covers(it, stored) }) throw NotFoundException("Not found")
            throw denied("not allowed to update this ${entity.name}")
        }

        val changed = entity.newEntity()
        entity.key.copy(stored, changed)
        for (fieldName in entity.fieldsSetIn(scope)) entity.field(fieldName).copy(stored, changed)
        for (fieldName in fields) entity.field(fieldName).copy(row, changed)

        val allowed = reaching.any { it.update.containsAll(fields) && covers(it, changed) }
        if (!allowed) throw denied("not allowed to update ${entity.name} ${notIn(fields, reaching) { it.update }}")
    }

    /** Restricts a delete to the rows of the grants that delete and read every field the criteria filters by. */
    fun restrictDelete(criteria: C) {
        if (requester == null) return
        val deleters = grants.filter { it.delete }
        if (deleters.isEmpty()) throw denied("not allowed to delete ${entity.name}")
        restrictRows(criteria, eligibleToFilter(criteria, deleters))
    }

    // :: Rows

    private fun readers(): List<DataGrant<E, C>> {
        val readers = grants.filter { it.reads }
        if (readers.isEmpty()) throw denied("not allowed to read ${entity.name}")
        return readers
    }

    /**
     * The grants that read, as they are, every field the criteria filters or sorts by. Filtering by a field reveals its
     * values, so it takes a grant that reads it; and the rows returned are only those of such grants.
     */
    private fun eligibleToFilter(criteria: C, candidates: List<DataGrant<E, C>>): List<DataGrant<E, C>> {
        val filtered = entity.filteredFields(criteria)
        val eligible = candidates.filter { it.read.containsAll(filtered) }
        if (eligible.isEmpty()) {
            throw denied("not allowed to filter or sort ${entity.name} by ${notIn(filtered, candidates) { it.read }}")
        }
        return eligible
    }

    /**
     * Adds to the criteria the rows the grants reach: nothing when one of them reaches every row; otherwise one
     * alternative group per row scope, each carrying the groups the criteria already had, so those alternatives still
     * hold inside the reachable rows.
     */
    private fun restrictRows(criteria: C, grants: List<DataGrant<E, C>>) {
        val scopes = ArrayList<RowScope<E, C>>()
        for (grant in grants) {
            if (grant.rows === AllRows) return
            if (scopes.none { it === grant.rows }) scopes.add(grant.rows)
        }
        val existing = entity.anyOf(criteria)
        val groups = scopes.map { scope ->
            entity.newCriteria().also { group ->
                scope.restrict(group, requester!!)
                entity.setAnyOf(group, existing)
            }
        }
        entity.setAnyOf(criteria, groups)
    }

    private suspend fun covers(grant: DataGrant<E, C>, row: E): Boolean =
        grant.rows === AllRows || grant.rows.covers(row, requester!!)

    // :: Helpers

    /**
     * The fields none of the grants declares; all of [fields] when each is declared by some grant — then what fails is
     * asking for them together, and the message names them all.
     */
    private fun notIn(fields: Set<String>, grants: List<DataGrant<E, C>>, declared: (DataGrant<E, C>) -> Set<String>): Set<String> {
        val missing = fields.filterTo(LinkedHashSet()) { field -> grants.none { field in declared(it) } }
        return missing.ifEmpty { fields }
    }

    private fun denied(message: String) = AccessDeniedException(message)
}
