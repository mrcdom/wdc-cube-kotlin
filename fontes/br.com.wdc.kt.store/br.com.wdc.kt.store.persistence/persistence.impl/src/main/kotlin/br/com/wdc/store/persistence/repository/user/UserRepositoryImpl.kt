package br.com.wdc.store.persistence.repository.user

import br.com.wdc.framework.domain.exception.InvalidRequestException
import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.framework.jooq.CriterionTranslator
import br.com.wdc.framework.jooq.JsonChildQueryBuilder
import br.com.wdc.framework.jooq.JsonQuery
import br.com.wdc.framework.jooq.JsonQueryBuilder
import br.com.wdc.store.domain.user.User
import br.com.wdc.store.domain.user.UserCriteria
import br.com.wdc.store.domain.user.UserCriteria.OrderBy
import br.com.wdc.store.domain.user.UserRepository
import br.com.wdc.store.persistence.repository.BaseRepositoryImpl
import br.com.wdc.store.persistence.scheme.sequences.SQ_USER
import br.com.wdc.store.persistence.scheme.tables.EnUser
import br.com.wdc.store.persistence.scheme.tables.references.EN_USER
import org.jooq.Condition
import org.jooq.SortField
import org.jooq.impl.DSL

class UserRepositoryImpl : BaseRepositoryImpl(), UserRepository {

    override suspend fun insert(bean: User, projection: User?): Boolean {
        // the given id is kept (the demo data load writes fixed ids)
        if (bean.id == null) bean.id = dsl().nextval(SQ_USER)
        return QUERY.insert(bean, projection)
    }

    override suspend fun update(newBean: User, oldBean: User?, projection: User?): Boolean {
        val id = newBean.id ?: throw InvalidRequestException("user update requires the id")
        // the default projection leaves out the password; to rewrite it, mark it in the projection (or pass the oldBean)
        val prj = projection ?: ownFieldsProjection().apply { if (oldBean != null) password = ProjectionValues.str }
        // no projection nor oldBean: compares with the empty entity, and only fields with a value go into the UPDATE
        val base = oldBean ?: if (projection == null) User() else null

        val step = dsl().update(EN_USER).set(EN_USER.ID, id)
        var hasChanges = false
        if (changed(newBean, base, prj) { it.userName }) {
            step.set(EN_USER.USERNAME, newBean.userName)
            hasChanges = true
        }
        if (changed(newBean, base, prj) { it.password }) {
            step.set(EN_USER.PASSWORD, newBean.password)
            hasChanges = true
        }
        if (changed(newBean, base, prj) { it.name }) {
            step.set(EN_USER.NAME, newBean.name)
            hasChanges = true
        }
        if (changed(newBean, base, prj) { it.roles }) {
            step.set(EN_USER.ROLES, newBean.roles)
            hasChanges = true
        }
        if (!hasChanges) {
            return false
        }
        return step.where(EN_USER.ID.eq(id)).execute() > 0
    }

    override suspend fun delete(criteria: UserCriteria): Int {
        if (criteria.criterions().none { it.isSet() }) {
            throw InvalidRequestException("user delete requires at least one criteria field set")
        }
        return dsl().deleteFrom(EN_USER).where(conditions(EN_USER, criteria)).execute()
    }

    override suspend fun count(criteria: UserCriteria): Int =
        dsl().selectCount().from(EN_USER).where(conditions(EN_USER, criteria)).fetchOne()!!.value1()

    override suspend fun fetch(criteria: UserCriteria, offset: Int, limit: Int): List<User> =
        QUERY.fetchToList(projectionFrom(criteria)) { t, q ->
            val step = q.where(conditions(t, criteria))
            step.orderBy(orderingOf(t, criteria))
            if (limit > 0) step.limit(limit)
            if (offset > 0) step.offset(offset)
        }

    /** The criteria's projection, with the id ensured; without one, the default. */
    private fun projectionFrom(criteria: UserCriteria): User {
        val projection = criteria.projection ?: return ownFieldsProjection()
        if (projection.id == null) {
            projection.id = 0L
        }
        return projection
    }

    companion object {
        val QUERY: JsonQuery<User, EnUser> = JsonQueryBuilder<User, EnUser>()
            .setAlias("u")
            .setBeanFactory(::User)
            .setTableFactory { alias -> EN_USER.`as`(alias) }
            .setDSLContextSupplier(BaseRepositoryImpl::dsl)
            .setOrdering(::orderingOf)
            .addI64("id", { it.id }, { b, v -> b.id = v }, { it.ID })
            .addStr("userName", { it.userName }, { b, v -> b.userName = v }, { it.USERNAME })
            .addStr("password", { it.password }, { b, v -> b.password = v }, { it.PASSWORD })
            .addStr("name", { it.name }, { b, v -> b.name = v }, { it.NAME })
            .addStr("roles", { it.roles }, { b, v -> b.roles = v }, { it.ROLES })
            .build()

        /** Every ordering by a non-unique field breaks ties by the key. */
        fun orderingOf(t: EnUser, criteria: Any?): List<SortField<*>> {
            val c = criteria as? UserCriteria ?: return emptyList()
            return when (c.orderBy ?: return emptyList()) {
                OrderBy.OLDEST_FIRST -> listOf(t.ID.asc())
                OrderBy.NEWEST_FIRST -> listOf(t.ID.desc())
                OrderBy.NAME_A_TO_Z -> listOf(t.NAME.asc(), t.ID.asc())
                OrderBy.LOGIN_A_TO_Z -> listOf(t.USERNAME.asc(), t.ID.asc())
            }
        }

        /** Condition of the user criteria in a relation (the criteria comes from the projection collection). */
        fun applyConditions(cq: JsonChildQueryBuilder<*, EnUser>): Condition =
            cq.criteriaAs<UserCriteria>()?.let { conditions(cq.childTable, it) } ?: DSL.noCondition()

        private fun conditions(t: EnUser, criteria: UserCriteria): Condition = CriterionTranslator.and(
            listOf(
                CriterionTranslator.translate(t.ID, criteria.userId),
                CriterionTranslator.translate(t.USERNAME, criteria.userName),
                CriterionTranslator.translate(t.NAME, criteria.name),
                CriterionTranslator.translate(t.ROLES, criteria.roles),
                CriterionTranslator.anyOf(criteria.anyOf) { conditions(t, it) },
            )
        )
    }
}
