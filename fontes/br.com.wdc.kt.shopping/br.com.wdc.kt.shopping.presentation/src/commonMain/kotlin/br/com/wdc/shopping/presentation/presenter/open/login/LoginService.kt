package br.com.wdc.shopping.presentation.presenter.open.login

import br.com.wdc.shopping.domain.user.UserCriteria
import br.com.wdc.shopping.domain.user.UserRepository
import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.shopping.domain.security.AuthenticationService
import br.com.wdc.shopping.domain.security.PasswordUtil
import br.com.wdc.shopping.presentation.presenter.RootPresenter
import br.com.wdc.shopping.presentation.presenter.open.login.structs.Subject

class LoginService(private val app: RootPresenter?) {

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
        // 1. Password hash (MD5 → base36, same format as in the database)
        val passwordHash = PasswordUtil.hashPassword(password)

        // 2. Get a challenge (single-use nonce)
        val challenge = authService.challenge()

        // 3. Compute HMAC-SHA256(key=passwordHash, data=userName+nonce)
        val digest = PasswordUtil.computeHmac(passwordHash, userName + challenge.nonce)

        // 4. Authenticate
        val authResult = authService.login(userName, digest, challenge.nonce) ?: return null

        // 5. Keep the session context in the application (on the server; on a REST client there is nothing to resolve)
        app!!.setSecurityContext(authService.resolveToken(authResult.accessToken))

        // 6. Fetch the user's display name
        val users = UserRepository.BEAN.get().fetch(
            UserCriteria().withUserId(authResult.userId).withProjection(Subject.projection()),
            limit = 1,
        )
        return if (users.isEmpty()) null else Subject.create(users[0])
    }

    /**
     * Without an authentication service (tests and local use without security): the password is not a criteria
     * field, so it searches by login only, projecting the password digest, and checks it here — the same thing the
     * authentication service does on the server.
     */
    private suspend fun authenticateViaRepository(userName: String, password: String): Subject? {
        val projection = Subject.projection().apply { this.password = ProjectionValues.str }
        val stored = UserRepository.BEAN.get()
            .fetch(UserCriteria().withUserName(userName).withProjection(projection), limit = 1)
            .firstOrNull() ?: return null
        // the column is CHAR(32): the digest comes padded with spaces
        if (stored.password?.trim() != PasswordUtil.hashPassword(password)) return null
        return Subject.create(stored)
    }
}
