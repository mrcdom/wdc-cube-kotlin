package br.com.wdc.shopping.persistence.client

import br.com.wdc.shopping.domain.criteria.UserCriteria
import br.com.wdc.shopping.domain.model.User
import br.com.wdc.shopping.domain.repositories.UserRepository

class RestUserRepository(private val config: RestConfig) : UserRepository {

    override fun insert(user: User): Boolean {
        val body = user.toMap().toMutableMap()
        val result = config.postJson("/api/repo/user/insert", body)
        val success = result.boolean("success")
        if (success && result.containsKey("id") && result["id"] != null) {
            user.id = result.long("id")
        }
        return success
    }

    override fun update(newUser: User, oldUser: User): Boolean {
        val body = mapOf(
            "newEntity" to newUser.toMap(),
            "oldEntity" to oldUser.toMap()
        )
        return config.postJson("/api/repo/user/update", body).boolean("success")
    }

    override fun insertOrUpdate(user: User): Boolean {
        val body = user.toMap().toMutableMap()
        val result = config.postJson("/api/repo/user/upsert", body)
        val success = result.boolean("success")
        if (success && result.containsKey("id") && result["id"] != null) {
            user.id = result.long("id")
        }
        return success
    }

    override fun delete(criteria: UserCriteria): Int {
        return config.postJson("/api/repo/user/delete", buildCriteria(criteria)).int("count")
    }

    override fun count(criteria: UserCriteria): Int {
        return config.postJson("/api/repo/user/count", buildCriteria(criteria)).int("count")
    }

    override fun fetch(criteria: UserCriteria): List<User> {
        val body = buildCriteria(criteria).toMutableMap()
        criteria.projection?.let { body["projection"] = it.toMap() }
        val result = config.postJson("/api/repo/user/fetch", body)
        return result.list("items").map { it.toUser() }
    }

    override fun fetchById(userId: Long, projection: User?): User? {
        val body = mutableMapOf<String, Any?>("id" to userId)
        projection?.let { body["projection"] = it.toMap() }
        val result = config.postJsonNullable("/api/repo/user/fetchById", body) ?: return null
        return result.toUser()
    }

    private fun buildCriteria(criteria: UserCriteria): Map<String, Any?> = buildMap {
        criteria.userId?.let { put("userId", it) }
        criteria.userName?.let { put("userName", it) }
        criteria.password?.let { put("password", it) }
        criteria.offset?.let { put("offset", it) }
        criteria.limit?.let { put("limit", it) }
        criteria.orderBy?.let { put("orderBy", it.name) }
    }
}
