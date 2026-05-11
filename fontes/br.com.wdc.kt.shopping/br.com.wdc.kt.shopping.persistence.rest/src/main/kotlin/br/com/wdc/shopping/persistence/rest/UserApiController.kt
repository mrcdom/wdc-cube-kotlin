package br.com.wdc.shopping.persistence.rest

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.shopping.domain.criteria.UserCriteria
import br.com.wdc.shopping.domain.model.User
import br.com.wdc.shopping.domain.repositories.UserRepository
import br.com.wdc.shopping.domain.security.SecurityContextHolder
import com.google.gson.JsonObject
import io.javalin.config.JavalinConfig
import io.javalin.http.Context
import java.nio.charset.StandardCharsets
import java.security.PrivateKey
import java.util.Base64
import javax.crypto.Cipher

class UserApiController {

    companion object {
        private val LOG = Log.getLogger("UserApiController")

        fun configure(config: JavalinConfig) {
            val ctrl = UserApiController()
            config.routes.post("/api/repo/user/insert", ctrl::insert)
            config.routes.post("/api/repo/user/update", ctrl::update)
            config.routes.post("/api/repo/user/upsert", ctrl::upsert)
            config.routes.post("/api/repo/user/delete", ctrl::delete)
            config.routes.post("/api/repo/user/count", ctrl::count)
            config.routes.post("/api/repo/user/fetch", ctrl::fetch)
            config.routes.post("/api/repo/user/fetchById", ctrl::fetchByIdPost)
            config.routes.get("/api/repo/user/{id}", ctrl::fetchById)
        }

        private fun repo(): UserRepository = UserRepository.BEAN.get()

        private fun parseCriteria(body: JsonObject): UserCriteria {
            val criteria = UserCriteria()
            if (hasValue(body, "userId")) criteria.withUserId(body.get("userId").asLong)
            if (hasValue(body, "userName")) criteria.withUserName(body.get("userName").asString)
            if (hasValue(body, "password")) criteria.withPassword(body.get("password").asString)
            if (hasValue(body, "offset")) criteria.withOffset(body.get("offset").asInt)
            if (hasValue(body, "limit")) criteria.withLimit(body.get("limit").asInt)
            if (hasValue(body, "orderBy")) criteria.withOrderBy(UserCriteria.OrderBy.valueOf(body.get("orderBy").asString))
            return criteria
        }

        private fun hasValue(obj: JsonObject, field: String): Boolean {
            return obj.has(field) && !obj.get(field).isJsonNull
        }

        private fun json(ctx: Context, obj: Any) {
            ctx.contentType("application/json")
            ctx.result(ApiGson.instance.toJson(obj))
        }

        /**
         * Decripta a senha se presente e criptografada com RSA (chave da sessão).
         */
        private fun decryptPasswordIfPresent(user: User) {
            val sc = SecurityContextHolder.get()
            if (sc != null && !user.password.isNullOrBlank()) {
                try {
                    user.password = rsaDecrypt(user.password!!, sc.privateKey!!)
                } catch (_: Exception) {
                    LOG.debug("Password not RSA-encrypted or decryption failed, using as-is")
                }
            }
        }

        private fun rsaDecrypt(encryptedBase64: String, privateKey: PrivateKey): String {
            val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
            cipher.init(Cipher.DECRYPT_MODE, privateKey)
            val decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedBase64))
            return String(decrypted, StandardCharsets.UTF_8)
        }
    }

    private fun insert(ctx: Context) {
        val user = ApiGson.instance.fromJson(ctx.body(), User::class.java)
        decryptPasswordIfPresent(user)
        val success = repo().insert(user)
        json(ctx, mapOf("success" to success, "id" to (user.id ?: -1L)))
    }

    private fun update(ctx: Context) {
        val body = ApiGson.instance.fromJson(ctx.body(), JsonObject::class.java)
        val newEntity = ApiGson.instance.fromJson(body.get("newEntity"), User::class.java)
        val oldEntity = ApiGson.instance.fromJson(body.get("oldEntity"), User::class.java)
        decryptPasswordIfPresent(newEntity)
        val success = repo().update(newEntity, oldEntity)
        json(ctx, mapOf("success" to success))
    }

    private fun upsert(ctx: Context) {
        val user = ApiGson.instance.fromJson(ctx.body(), User::class.java)
        decryptPasswordIfPresent(user)
        val success = repo().insertOrUpdate(user)
        json(ctx, mapOf("success" to success, "id" to (user.id ?: -1L)))
    }

    private fun delete(ctx: Context) {
        val body = ApiGson.instance.fromJson(ctx.body(), JsonObject::class.java)
        val count = repo().delete(parseCriteria(body))
        json(ctx, mapOf("count" to count))
    }

    private fun count(ctx: Context) {
        val body = ApiGson.instance.fromJson(ctx.body(), JsonObject::class.java)
        val count = repo().count(parseCriteria(body))
        json(ctx, mapOf("count" to count))
    }

    private fun fetch(ctx: Context) {
        val body = ApiGson.instance.fromJson(ctx.body(), JsonObject::class.java)
        val criteria = parseCriteria(body)
        val projection = ApiGson.parseProjection(body, User::class.java)
        criteria.withProjection(projection)
        val items = repo().fetch(criteria)
        json(ctx, mapOf("items" to items))
    }

    private fun fetchById(ctx: Context) {
        val id = ctx.pathParam("id").toLong()
        val result = repo().fetchById(id, null)
        if (result == null) {
            ctx.status(404).json(mapOf("error" to "Not found"))
            return
        }
        json(ctx, result)
    }

    private fun fetchByIdPost(ctx: Context) {
        val body = ApiGson.instance.fromJson(ctx.body(), JsonObject::class.java)
        val id = body.get("id").asLong
        val projection = ApiGson.parseProjection(body, User::class.java)
        val result = repo().fetchById(id, projection)
        if (result == null) {
            ctx.status(404).json(mapOf("error" to "Not found"))
            return
        }
        json(ctx, result)
    }
}
