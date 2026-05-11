package br.com.wdc.shopping.presentation.presenter.open.login

import br.com.wdc.shopping.domain.criteria.UserCriteria
import br.com.wdc.shopping.domain.repositories.UserRepository
import br.com.wdc.shopping.domain.security.AuthenticationService
import br.com.wdc.shopping.domain.security.PasswordUtil
import br.com.wdc.shopping.domain.security.SecurityContextHolder
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.presenter.open.login.structs.Subject

class LoginService(private val app: ShoppingApplication?) {

    fun fetchSubject(userName: String, password: String): Subject? {
        val authService = AuthenticationService.BEAN.getOrNull()
        if (authService != null) {
            return authenticateViaAuthService(authService, userName, password)
        }
        return authenticateViaRepository(userName, password)
    }

    private fun authenticateViaAuthService(
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
            UserCriteria()
                .withUserId(authResult.userId)
                .withProjection(Subject.projection())
                .withLimit(1)
        )
        return if (users.isEmpty()) null else Subject.create(users[0])
    }

    private fun authenticateViaRepository(userName: String, password: String): Subject? {
        val repository = UserRepository.BEAN.get()
        return repository.fetch(
            UserCriteria()
                .withUserName(userName)
                .withPassword(password)
                .withProjection(Subject.projection())
                .withLimit(1)
        ).map { Subject.create(it) }.firstOrNull()
    }
}
