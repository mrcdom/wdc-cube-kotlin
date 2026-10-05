package br.com.wdc.shopping.presentation.presenter.open.login

import br.com.wdc.shopping.domain.user.UserCriteria
import br.com.wdc.shopping.domain.user.UserRepository
import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.shopping.domain.security.AuthenticationService
import br.com.wdc.shopping.domain.security.PasswordUtil
import br.com.wdc.shopping.domain.security.SecurityContextHolder
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.presenter.open.login.structs.Subject

class LoginService(private val app: ShoppingApplication?) {

    suspend fun fetchSubject(userName: String, password: String): Subject? {
        val authService = AuthenticationService.BEAN.getOrNull()
        if (authService != null) {
            return authenticateViaAuthService(authService, userName, password)
        }
        return authenticateViaRepository(userName, password)
    }

    private suspend fun authenticateViaAuthService(
        authService: AuthenticationService,
        userName: String,
        password: String,
    ): Subject? {
        // 1. Hash da senha (MD5 → base36, mesmo formato do banco)
        val passwordHash = PasswordUtil.hashPassword(password)

        // 2. Obter challenge (nonce de uso único)
        val challenge = authService.challenge()

        // 3. Calcular HMAC-SHA256(key=passwordHash, data=userName+nonce)
        val digest = PasswordUtil.computeHmac(passwordHash, userName + challenge.nonce)

        // 4. Autenticar
        val authResult = authService.login(userName, digest, challenge.nonce) ?: return null

        // 5. Resolver token → SecurityContext (server-side; null em REST client)
        val securityContext = authService.resolveToken(authResult.accessToken)
        if (securityContext != null) {
            SecurityContextHolder.set(securityContext)
        }

        // 6. Armazenar SecurityContext na aplicação (para delegates de repositório)
        app!!.setSecurityContext(securityContext)

        // 7. Buscar nome de exibição do usuário
        val users = app!!.getUserRepository().fetch(
            UserCriteria().withUserId(authResult.userId).withProjection(Subject.projection()),
            limit = 1,
        )
        return if (users.isEmpty()) null else Subject.create(users[0])
    }

    /**
     * Sem serviço de autenticação (testes e uso local sem segurança): a senha não é campo de critério, então
     * busca só pelo login, projetando o resumo da senha, e o confere aqui — o mesmo que o serviço de
     * autenticação faz no servidor.
     */
    private suspend fun authenticateViaRepository(userName: String, password: String): Subject? {
        val projection = Subject.projection().apply { this.password = ProjectionValues.str }
        val stored = UserRepository.BEAN.get()
            .fetch(UserCriteria().withUserName(userName).withProjection(projection), limit = 1)
            .firstOrNull() ?: return null
        // a coluna é CHAR(32): o resumo vem completado com espaços
        if (stored.password?.trim() != PasswordUtil.hashPassword(password)) return null
        return Subject.create(stored)
    }
}
