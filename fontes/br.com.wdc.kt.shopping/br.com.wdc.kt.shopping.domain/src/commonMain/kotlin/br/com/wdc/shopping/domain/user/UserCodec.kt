package br.com.wdc.shopping.domain.user

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.InputCoerceUtils
import br.com.wdc.framework.domain.codec.EntityGraph
import br.com.wdc.framework.domain.codec.ModelCodec
import br.com.wdc.framework.domain.criteria.CriterionCodec
import br.com.wdc.framework.domain.projection.ProjectionValues

/**
 * Como [User] e [UserCriteria] trafegam — o mesmo codec no cliente e no servidor.
 *
 * A senha trafega quando está na entidade (gravação) ou marcada na projeção; o que impede que ela saia do
 * servidor é o repositório com segurança, que a remove do que devolve.
 */
class UserCodec : ModelCodec<User, UserCriteria> {

    override fun writeEntity(out: ExtensibleObjectOutput, entity: User, graph: EntityGraph) {
        out.beginObject()
        entity.id?.let { out.name("id").value(it) }
        if (graph.track(entity)) {
            entity.userName?.let { out.name("userName").value(it) }
            entity.name?.let { out.name("name").value(it) }
            entity.password?.let { out.name("password").value(it) }
            entity.roles?.let { out.name("roles").value(it) }
        }
        out.endObject()
    }

    override fun writeEntityProjected(out: ExtensibleObjectOutput, entity: User, projection: User) {
        out.beginObject()
        entity.id?.let { out.name("id").value(it) }
        writeIfProjected(out, "userName", projection.userName, entity.userName)
        writeIfProjected(out, "name", projection.name, entity.name)
        writeIfProjected(out, "password", projection.password, entity.password)
        writeIfProjected(out, "roles", projection.roles, entity.roles)
        out.endObject()
    }

    private fun writeIfProjected(out: ExtensibleObjectOutput, name: String, marker: String?, value: String?) {
        if (marker != null) {
            out.name(name)
            if (value != null) out.value(value) else out.nullValue()
        }
    }

    override fun computeProjection(newEntity: User, oldEntity: User): User {
        val pv = ProjectionValues
        val projection = User()
        if (newEntity.userName != oldEntity.userName) projection.userName = pv.str
        if (newEntity.name != oldEntity.name) projection.name = pv.str
        if (newEntity.password != oldEntity.password) projection.password = pv.str
        if (newEntity.roles != oldEntity.roles) projection.roles = pv.str
        return projection
    }

    override fun readEntity(input: ExtensibleObjectInput): User {
        val user = User()
        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                "id" -> user.id = InputCoerceUtils.asLong(input)
                "userName" -> user.userName = InputCoerceUtils.asString(input)
                "name" -> user.name = InputCoerceUtils.asString(input)
                "password" -> user.password = InputCoerceUtils.asString(input)
                "roles" -> user.roles = InputCoerceUtils.asString(input)
                else -> input.skipValue()
            }
        }
        input.endObject()
        return user
    }

    override fun readEntityForUpdate(input: ExtensibleObjectInput): ModelCodec.UpdateData<User> {
        val pv = ProjectionValues
        val entity = User()
        val projection = User()
        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                "id" -> { entity.id = InputCoerceUtils.asLong(input); projection.id = pv.i64 }
                "userName" -> { entity.userName = InputCoerceUtils.asString(input); projection.userName = pv.str }
                "name" -> { entity.name = InputCoerceUtils.asString(input); projection.name = pv.str }
                "password" -> { entity.password = InputCoerceUtils.asString(input); projection.password = pv.str }
                "roles" -> { entity.roles = InputCoerceUtils.asString(input); projection.roles = pv.str }
                else -> input.skipValue()
            }
        }
        input.endObject()
        return ModelCodec.UpdateData(entity, projection)
    }

    override fun writeCriteriaFields(out: ExtensibleObjectOutput, criteria: UserCriteria) {
        CriterionCodec.write(out, "userId", criteria.userId, CriterionCodec.LONG_OUT)
        CriterionCodec.write(out, "userName", criteria.userName, CriterionCodec.STRING_OUT)
        CriterionCodec.write(out, "name", criteria.name, CriterionCodec.STRING_OUT)
        CriterionCodec.write(out, "roles", criteria.roles, CriterionCodec.STRING_OUT)
        criteria.orderBy?.let { out.name("orderBy").value(it.name) }
    }

    override fun readCriteriaField(input: ExtensibleObjectInput, fieldName: String, criteria: UserCriteria): Boolean {
        when (fieldName) {
            "userId" -> CriterionCodec.read(input, criteria.userId, CriterionCodec.LONG_IN)
            "userName" -> CriterionCodec.read(input, criteria.userName, CriterionCodec.STRING_IN)
            "name" -> CriterionCodec.read(input, criteria.name, CriterionCodec.STRING_IN)
            "roles" -> CriterionCodec.read(input, criteria.roles, CriterionCodec.STRING_IN)
            "orderBy" -> CriterionCodec.readOrderBy(input, UserCriteria.OrderBy.entries)?.let { criteria.withOrderBy(it) }
            else -> return false
        }
        return true
    }

    override fun getProjection(criteria: UserCriteria): User? = criteria.projection

    override fun setGeneratedId(entity: User, id: Long) {
        entity.id = id
    }
}
