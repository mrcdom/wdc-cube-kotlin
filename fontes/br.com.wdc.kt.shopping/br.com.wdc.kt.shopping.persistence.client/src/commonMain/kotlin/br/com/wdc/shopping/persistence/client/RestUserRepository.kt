package br.com.wdc.shopping.persistence.client

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.SerializationToken
import br.com.wdc.shopping.domain.criteria.UserCriteria
import br.com.wdc.shopping.domain.model.User
import br.com.wdc.shopping.domain.repositories.UserRepository

class RestUserRepository(private val config: RestConfig) : UserRepository {

    override fun insert(user: User): Boolean {
        val body = config.toJson { user.writeTo(it) }
        val input = config.postJson("/api/repo/user/insert", body)
        return readSuccessWithId(input, user)
    }

    override fun update(newUser: User, oldUser: User): Boolean {
        val body = config.toJson { out ->
            out.beginObject()
            out.name("newEntity"); newUser.writeTo(out)
            out.name("oldEntity"); oldUser.writeTo(out)
            out.endObject()
        }
        return readSuccess(config.postJson("/api/repo/user/update", body))
    }

    override fun insertOrUpdate(user: User): Boolean {
        val body = config.toJson { user.writeTo(it) }
        val input = config.postJson("/api/repo/user/upsert", body)
        return readSuccessWithId(input, user)
    }

    override fun delete(criteria: UserCriteria): Int {
        val body = config.toJson { writeCriteria(it, criteria) }
        return readCount(config.postJson("/api/repo/user/delete", body))
    }

    override fun count(criteria: UserCriteria): Int {
        val body = config.toJson { writeCriteria(it, criteria) }
        return readCount(config.postJson("/api/repo/user/count", body))
    }

    override fun fetch(criteria: UserCriteria): List<User> {
        val body = config.toJson { out ->
            out.beginObject()
            writeCriteriaFields(out, criteria)
            criteria.projection?.let { out.name("projection"); it.writeTo(out) }
            out.endObject()
        }
        val input = config.postJson("/api/repo/user/fetch", body)
        return readUserList(input)
    }

    override fun fetchById(userId: Long, projection: User?): User? {
        val body = config.toJson { out ->
            out.beginObject()
            out.name("id").value(userId)
            projection?.let { out.name("projection"); it.writeTo(out) }
            out.endObject()
        }
        val input = config.postJsonNullable("/api/repo/user/fetchById", body) ?: return null
        return input.readUser()
    }

    private fun readSuccessWithId(input: ExtensibleObjectInput, user: User): Boolean {
        var success = false
        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                "success" -> success = input.nextBoolean()
                "id" -> {
                    if (input.peek() != SerializationToken.NULL) {
                        user.id = input.nextLong()
                    } else {
                        input.nextNull<Any>()
                    }
                }
                else -> input.skipValue()
            }
        }
        input.endObject()
        return success
    }

    private fun writeCriteria(out: ExtensibleObjectOutput, criteria: UserCriteria) {
        out.beginObject()
        writeCriteriaFields(out, criteria)
        out.endObject()
    }

    private fun writeCriteriaFields(out: ExtensibleObjectOutput, criteria: UserCriteria) {
        criteria.userId?.let { out.name("userId").value(it) }
        criteria.userName?.let { out.name("userName").value(it) }
        criteria.password?.let { out.name("password").value(it) }
        criteria.offset?.let { out.name("offset").value(it.toLong()) }
        criteria.limit?.let { out.name("limit").value(it.toLong()) }
        criteria.orderBy?.let { out.name("orderBy").value(it.name) }
    }

    private fun readUserList(input: ExtensibleObjectInput): List<User> {
        val result = mutableListOf<User>()
        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                "items" -> {
                    input.beginArray()
                    while (input.hasNext()) { result.add(input.readUser()) }
                    input.endArray()
                }
                else -> input.skipValue()
            }
        }
        input.endObject()
        return result
    }
}
