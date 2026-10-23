package br.com.wdc.shopping.persistence.rest

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.rest.RequestContext
import br.com.wdc.framework.rest.RestApi
import br.com.wdc.framework.rest.rpc.RepositoryRpc
import br.com.wdc.shopping.domain.exception.AccessDeniedException
import br.com.wdc.shopping.domain.user.User
import br.com.wdc.shopping.domain.user.UserCodec
import br.com.wdc.shopping.domain.user.UserCriteria
import br.com.wdc.shopping.domain.user.UserRepository
import br.com.wdc.shopping.persistence.rest.doc.RepositoryApiDocs
import java.nio.charset.StandardCharsets
import java.security.PrivateKey
import java.util.Base64
import javax.crypto.Cipher

/**
 * Endpoints REST de usuário: o RPC do repositório, lido e escrito com o [UserCodec] — o mesmo que o cliente usa.
 * O acesso é conferido aqui ([ApiSecurity]): permissão e, para quem não alcança os dados de todos, restrição ao
 * próprio usuário.
 *
 * **A senha é só de escrita nesta API**: entra no insert e no update, e nunca é devolvida — nem o resumo, nem
 * com a segurança desligada, nem quando pedida na projeção.
 */
internal object UserApiController {

    private val LOG = Log.getLogger("UserApiController")
    private const val ENTITY = "user"

    private fun repo(): UserRepository = UserRepository.BEAN.get()

    fun register(routes: RestApi.Routes) {
        val rpc = RepositoryRpc<User, UserCriteria, Long>(
            basePath = "/api/repo/user",
            codec = UserCodec(),
            repository = ::repo,
            newCriteria = ::UserCriteria,
            setProjection = { c, p -> c.withProjection(p) },
            restrictToId = { c, id -> c.withUserId(id) },
            parseId = String::toLongOrNull,
            idOf = { it.id },
        )
        rpc.rule = { op -> ApiSecurity.permission(ENTITY, op) }
        rpc.doc = { op -> RepositoryApiDocs.rpcDoc("user", op) }
        // quem não alcança os dados de todos só alcança o próprio usuário, peça o que pedir
        rpc.scope = { criteria, request -> ApiSecurity.ownerScope(request)?.let { criteria.userId.eq(it) } }
        // a senha nunca é projetada, e é retirada de tudo o que sai
        rpc.sanitizeProjection = { it.password = null }
        rpc.sanitizeOutput = { it.password = null }
        rpc.beforeInsert = { user, request -> decryptPasswordIfPresent(user, request) }
        rpc.beforeUpdate = { data, request ->
            val owner = ApiSecurity.ownerScope(request)
            if (owner != null && data.entity.id != null && data.entity.id != owner) {
                throw AccessDeniedException("Cannot modify other user's data")
            }
            decryptPasswordIfPresent(data.entity, request)
        }
        rpc.register(routes)
    }

    /** Decifra a senha, se veio cifrada com a chave RSA da sessão; senão, fica como veio. */
    private fun decryptPasswordIfPresent(user: User, request: RequestContext) {
        val sc = ApiSecurity.context(request)
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
