package br.com.wdc.store.domain.security

/** What a client hears about the session it holds ([AuthenticationService.setSessionListener]). */
interface SessionListener {

    /**
     * The session was renewed: new tokens replaced the old ones, and the old refresh token no longer works. Whoever
     * keeps the session between runs ([AuthenticationService.writeAuthState]) keeps the new one.
     */
    fun sessionRenewed()

    /**
     * The server refused to renew the session (its refresh token expired, was revoked, or the server no longer
     * knows it): the client already forgot it, and the user has to sign in again. A failure to reach the server is
     * not a lost session.
     */
    fun sessionLost()
}
