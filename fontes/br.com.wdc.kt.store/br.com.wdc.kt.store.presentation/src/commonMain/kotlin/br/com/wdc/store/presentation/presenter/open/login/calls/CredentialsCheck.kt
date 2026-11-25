package br.com.wdc.store.presentation.presenter.open.login.calls

import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.framework.domain.exception.BusinessException
import br.com.wdc.framework.domain.resource.ResourceCall
import br.com.wdc.store.domain.security.AuthenticationService
import br.com.wdc.store.domain.security.PasswordUtil
import br.com.wdc.store.domain.security.Scram
import br.com.wdc.store.domain.security.ScramVerifier
import br.com.wdc.store.domain.user.UserCriteria
import br.com.wdc.store.domain.user.UserRepository
import br.com.wdc.store.presentation.presenter.open.login.structs.Subject

/**
 * Checks a user name and password, and gives who it is: the login. Not a read nor a write of a resource, so a verb of
 * its own on [ResourceCall].
 *
 * With the authentication service (security on) it is a SCRAM-SHA-256 exchange ([Scram]): the password never leaves
 * this process, and the server must prove it holds the user's verifier. Without it, the password is checked against
 * the verifier here. `null` when the credentials are wrong; too many failures for the user make it wait
 * ([br.com.wdc.framework.domain.exception.TooManyRequestsException]).
 */
class CredentialsCheck(private val userName: String, private val password: String) : ResourceCall<Subject?>() {

    private val userRepo: UserRepository get() = UserRepository.BEAN.get()

    override suspend fun run(): Subject? {
        val authService = AuthenticationService.BEAN.getOrNull()
        if (authService != null) {
            return authenticateViaAuthService(authService)
        }
        return authenticateViaRepository()
    }

    private suspend fun authenticateViaAuthService(authService: AuthenticationService): Subject? {
        val challenge = authService.challenge(userName)
        val response = Scram.current().respond(
            userName = userName,
            password = password,
            challenge = challenge,
        )
        val authResult = authService.login(
            userName = userName,
            proof = response.proof,
            nonce = challenge.nonce,
        ) ?: return null

        if (!response.serverIsAuthentic(authResult.serverSignature)) {
            authService.logout(authResult.refreshToken)
            throw BusinessException("The server could not prove it holds the user's credentials")
        }

        // who signed in; a session without a user to show is no sign-in, and is ended
        val users = userRepo.fetch(
            UserCriteria().withUserId(authResult.userId).withProjection(Subject.projection()),
            limit = 1,
        )
        if (users.isEmpty()) {
            authService.logout(authResult.refreshToken)
            return null
        }
        return Subject.create(users[0])
    }

    /**
     * Without an authentication service (tests and local use without security): the password is not a criteria
     * field, so it searches by login only, projecting the stored verifier, and checks the password against it — the
     * same check the authentication service makes on the server.
     */
    private suspend fun authenticateViaRepository(): Subject? {
        val projection = Subject.projection().apply { this.password = ProjectionValues.str }
        val stored = userRepo
            .fetch(UserCriteria().withUserName(userName).withProjection(projection), limit = 1)
            .firstOrNull() ?: return null
        val verifier = ScramVerifier.parse(stored.password) ?: return null
        val scram = Scram.current()
        val salted = scram.saltedPassword(
            passwordDigest = PasswordUtil.hashPassword(password),
            salt = verifier.salt,
            iterations = verifier.iterations,
        )
        if (!Scram.constantTimeEquals(scram.storedKey(scram.clientKey(salted)), verifier.storedKey)) return null
        return Subject.create(stored)
    }
}
