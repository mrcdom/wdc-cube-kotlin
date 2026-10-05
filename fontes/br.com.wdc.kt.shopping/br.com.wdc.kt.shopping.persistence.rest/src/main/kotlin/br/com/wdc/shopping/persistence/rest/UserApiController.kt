package br.com.wdc.shopping.persistence.rest

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.serialization.InputCoerceUtils
import br.com.wdc.framework.domain.exception.InvalidRequestException
import br.com.wdc.shopping.domain.security.SecurityContextHolder
import br.com.wdc.shopping.domain.user.User
import br.com.wdc.shopping.domain.user.UserCodec
import br.com.wdc.shopping.domain.user.UserCriteria
import br.com.wdc.shopping.domain.user.UserRepository
import io.javalin.config.JavalinConfig
import io.javalin.http.Context
import java.nio.charset.StandardCharsets
import java.security.PrivateKey
import java.util.Base64
import javax.crypto.Cipher

/**
 * Endpoints REST de usuário. Lê e escreve com o [UserCodec] — o mesmo que o cliente usa —, sem reflexão.
 * O controle de acesso e o escopo por usuário são do repositório registrado (decorado quando a segurança está
 * ligada).
 *
 * **A senha é só de escrita nesta API**: entra no insert e no update, e nunca é devolvida — nem o resumo, nem
 * com a segurança desligada, nem quando pedida na projeção.
 */
class UserApiController {

    companion object {
        private val LOG = Log.getLogger("UserApiController")

        fun configure(config: JavalinConfig) {
            val ctrl = UserApiController()
            config.routes.post("/api/repo/user/insert", ctrl::insert)
            config.routes.post("/api/repo/user/update", ctrl::update)
            config.routes.post("/api/repo/user/delete", ctrl::delete)
            config.routes.post("/api/repo/user/count", ctrl::count)
            config.routes.post("/api/repo/user/fetch", ctrl::fetch)
            config.routes.post("/api/repo/user/fetch-page", ctrl::fetchPage)
            config.routes.post("/api/repo/user/fetch-by-id", ctrl::fetchByIdPost)
            config.routes.get("/api/repo/user/{id}", ctrl::fetchById)
        }

        private fun repo(): UserRepository = UserRepository.BEAN.get()

        /** Decifra a senha, se veio cifrada com a chave RSA da sessão; senão, fica como veio. */
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

    private val codec = UserCodec()

    /** Lê o pedido de consulta; sem projeção, vale a padrão do repositório. A senha nunca é projetada. */
    private fun readFetchRequest(ctx: Context): FetchRequest<UserCriteria> {
        val request = codec.readFetchRequest(ctx.jsonBody(), UserCriteria()) { c, prj -> c.withProjection(prj) }
        if (request.criteria.projection == null) {
            request.criteria.withProjection(repo().newProjection())
        }
        request.criteria.projection?.password = null
        return request
    }

    /** Última barreira: o que vai ser escrito na resposta não leva senha. */
    private fun withoutPassword(users: List<User>): List<User> = users.onEach { it.password = null }

    private fun insert(ctx: Context) {
        val user = codec.readEntity(ctx.jsonBody())
        decryptPasswordIfPresent(user)
        val success = blocking { repo().insert(user) }
        ctx.jsonResult { it.beginObject().name("success").value(success).name("id").value(user.id ?: -1L).endObject() }
    }

    /** As chaves presentes no corpo dizem o que atualizar — inclusive para `null`. */
    private fun update(ctx: Context) {
        val data = codec.readEntityForUpdate(ctx.jsonBody())
        decryptPasswordIfPresent(data.entity)
        val success = blocking { repo().update(data.entity, null, data.projection) }
        ctx.jsonField("success", success)
    }

    private fun delete(ctx: Context) {
        val criteria = readFetchRequest(ctx).criteria
        ctx.jsonField("count", blocking { repo().delete(criteria) })
    }

    private fun count(ctx: Context) {
        val criteria = readFetchRequest(ctx).criteria
        ctx.jsonField("count", blocking { repo().count(criteria) })
    }

    private fun fetch(ctx: Context) {
        val request = readFetchRequest(ctx)
        val items = blocking { repo().fetch(request.criteria, request.offset, request.limit) }
        ctx.jsonResult { codec.writeItems(it, withoutPassword(items)) }
    }

    private fun fetchPage(ctx: Context) {
        val request = readFetchRequest(ctx)
        val page = blocking { repo().fetchPage(request.criteria, request.page, request.pageSize) }
        ctx.jsonResult { codec.writeItems(it, withoutPassword(page.items), page.totalItems) }
    }

    private fun fetchById(ctx: Context) {
        val id = ctx.pathParam("id").toLongOrNull() ?: throw InvalidRequestException("id de usuário inválido")
        respondEntity(ctx, blocking { repo().fetchById(id) })
    }

    private fun fetchByIdPost(ctx: Context) {
        var id: Long? = null
        var projection: User? = null
        val input = ctx.jsonBody()
        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                "id" -> id = InputCoerceUtils.asLong(input)
                "projection" -> projection = codec.readEntity(input)
                else -> input.skipValue()
            }
        }
        input.endObject()
        val userId = id ?: throw InvalidRequestException("fetch-by-id exige o id")
        projection?.password = null
        respondEntity(ctx, blocking { repo().fetchById(userId, projection) })
    }

    private fun respondEntity(ctx: Context, user: User?) {
        if (user == null) {
            ctx.status(404).json(mapOf("error" to "Not found"))
            return
        }
        user.password = null
        ctx.jsonResult { codec.writeEntity(it, user) }
    }
}
