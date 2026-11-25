package br.com.wdc.store.persistence.rest

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.domain.exception.InvalidRequestException
import br.com.wdc.framework.rest.RequestContext
import br.com.wdc.framework.rest.RestApi
import br.com.wdc.framework.rest.rpc.RepositoryRpc
import br.com.wdc.store.domain.user.User
import br.com.wdc.store.domain.user.UserCodec
import br.com.wdc.store.domain.user.UserCriteria
import br.com.wdc.store.domain.user.UserRepository
import br.com.wdc.store.persistence.rest.doc.RepositoryApiDocs
import br.com.wdc.store.persistence.rest.security.UserDataPolicy
import br.com.wdc.store.persistence.security.PasswordVerifiers
import java.nio.charset.StandardCharsets
import java.security.PrivateKey
import java.util.Base64
import javax.crypto.Cipher

/**
 * User REST endpoints: the repository RPC, read and written with the [UserCodec] — the same one the client uses.
 * Access is decided by [UserDataPolicy].
 *
 * **The password is write-only in this API**: it goes in on insert and update, and is never returned — asking for it
 * in a projection is refused (403, naming `password`), with security on or off. It is stored as a SCRAM-SHA-256
 * verifier with a fresh salt ([PasswordVerifiers]), which the login checks and which does not let anyone sign in:
 * with security on it must come encrypted with the session's RSA key (otherwise 400); without security there is no
 * key, and it comes as typed.
 */
internal object UserApiController {

    private val LOG = Log.getLogger("UserApiController")
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
        rpc.policy = UserDataPolicy.POLICY
        rpc.doc = { op -> RepositoryApiDocs.rpcDoc("user", op) }
        // transformations, after the policy's check: the password is stored as its verifier
        rpc.beforeInsert = { user, request -> storablePassword(user, request) }
        rpc.beforeUpdate = { data, request -> storablePassword(data.entity, request) }
        rpc.register(routes)
    }

    /**
     * Turns the password that came into the one stored: its verifier. With a session, the password must come encrypted
     * with the session's RSA key; one that does not decrypt is refused, never stored as it came.
     */
    private fun storablePassword(user: User, request: RequestContext) {
        val password = user.password?.takeIf { it.isNotBlank() } ?: return
        val sc = ApiSecurity.context(request)
        val plain = if (sc == null) {
            password
        } else {
            try {
                rsaDecrypt(password, sc.privateKey!!)
            } catch (e: Exception) {
                LOG.debug("Password not encrypted with the session key: {}", e.message)
                throw InvalidRequestException("The password must come encrypted with the session key")
            }
        }
        user.password = PasswordVerifiers.fromPassword(plain)
    }

    private fun rsaDecrypt(encryptedBase64: String, privateKey: PrivateKey): String {
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        val decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedBase64))
        return String(decrypted, StandardCharsets.UTF_8)
    }
}
