package br.com.wdc.shopping.persistence.repository.user

import br.com.wdc.framework.domain.exception.InvalidRequestException
import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.framework.domain.repository.Repository.Companion.changed
import br.com.wdc.framework.jooq.CriterionTranslator
import br.com.wdc.framework.jooq.JsonChildQueryBuilder
import br.com.wdc.framework.jooq.JsonQuery
import br.com.wdc.framework.jooq.JsonQueryBuilder
import br.com.wdc.shopping.domain.user.User
import br.com.wdc.shopping.domain.user.UserCriteria
import br.com.wdc.shopping.domain.user.UserCriteria.OrderBy
import br.com.wdc.shopping.domain.user.UserRepository
import br.com.wdc.shopping.persistence.repository.BaseRepositoryImpl
import br.com.wdc.shopping.persistence.scheme.sequences.SQ_USER
import br.com.wdc.shopping.persistence.scheme.tables.EnUser
import br.com.wdc.shopping.persistence.scheme.tables.references.EN_USER
import org.jooq.Condition
import org.jooq.SortField
import org.jooq.impl.DSL

class UserRepositoryImpl : BaseRepositoryImpl(), UserRepository {

    override suspend fun insert(bean: User): Boolean {
        val dsl = dsl()
        // o id informado é respeitado (a carga de demonstração grava ids fixos)
        val id = bean.id ?: dsl.nextval(SQ_USER).also { bean.id = it }
        val step = dsl.insertInto(EN_USER).set(EN_USER.ID, id)
        bean.userName?.let { step.set(EN_USER.USERNAME, it) }
        bean.password?.let { step.set(EN_USER.PASSWORD, it) }
        bean.name?.let { step.set(EN_USER.NAME, it) }
        bean.roles?.let { step.set(EN_USER.ROLES, it) }
        return step.execute() > 0
    }

    override suspend fun update(newBean: User, oldBean: User?, projection: User?): Boolean {
        val id = newBean.id ?: throw InvalidRequestException("update de usuário exige o id")
        // a projeção padrão não traz a senha; para regravá-la, marque-a na projeção (ou informe o oldBean)
        val prj = projection ?: newProjection().apply { if (oldBean != null) password = ProjectionValues.str }

        val step = dsl().update(EN_USER).set(EN_USER.ID, id)
        var hasChanges = false
        if (changed(newBean, oldBean, prj) { it.userName }) {
            step.set(EN_USER.USERNAME, newBean.userName)
            hasChanges = true
        }
        if (changed(newBean, oldBean, prj) { it.password }) {
            step.set(EN_USER.PASSWORD, newBean.password)
            hasChanges = true
        }
        if (changed(newBean, oldBean, prj) { it.name }) {
            step.set(EN_USER.NAME, newBean.name)
            hasChanges = true
        }
        if (changed(newBean, oldBean, prj) { it.roles }) {
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
            throw InvalidRequestException("delete de usuário exige ao menos um campo de critério informado")
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

    /** A projeção do critério, com o id garantido; sem projeção, a padrão. */
    private fun projectionFrom(criteria: UserCriteria): User {
        val projection = criteria.projection ?: return newProjection()
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

        /** Toda ordenação por campo não único desempata pela chave. */
        fun orderingOf(t: EnUser, criteria: Any?): List<SortField<*>> {
            val c = criteria as? UserCriteria ?: return emptyList()
            return when (c.orderBy ?: return emptyList()) {
                OrderBy.OLDEST_FIRST -> listOf(t.ID.asc())
                OrderBy.NEWEST_FIRST -> listOf(t.ID.desc())
                OrderBy.NAME_A_TO_Z -> listOf(t.NAME.asc(), t.ID.asc())
                OrderBy.LOGIN_A_TO_Z -> listOf(t.USERNAME.asc(), t.ID.asc())
            }
        }

        /** Condição do critério do usuário numa relação (o critério vem da coleção de projeção). */
        fun applyConditions(cq: JsonChildQueryBuilder<*, EnUser>): Condition =
            cq.criteriaAs<UserCriteria>()?.let { conditions(cq.childTable, it) } ?: DSL.noCondition()

        private fun conditions(t: EnUser, criteria: UserCriteria): Condition = CriterionTranslator.and(
            listOf(
                CriterionTranslator.translate(t.ID, criteria.userId),
                CriterionTranslator.translate(t.USERNAME, criteria.userName),
                CriterionTranslator.translate(t.NAME, criteria.name),
                CriterionTranslator.translate(t.ROLES, criteria.roles),
            )
        )
    }
}
