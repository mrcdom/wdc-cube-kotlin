package br.com.wdc.store.view.react

/**
 * The page at `/`: what the WDC Store is, and a card for each browser client, with a link when it is deployed and the
 * command that deploys it when it is not. Without security (no `security.jwt.secret`), it warns that the clients that
 * sign in through `/api/auth` cannot sign in.
 *
 * The page is `landing/index.html` in the resources; this fills in its markers.
 */
internal class LandingPage(
    /** Which clients are there to open: `remote-react`, `remote-kotlin`, `native`, `compose`. */
    available: Set<String>,
    securityOn: Boolean,
) {

    companion object {
        val CLIENTS = listOf("remote-react", "remote-kotlin", "native", "compose")

        private val TEMPLATE: String by lazy {
            LandingPage::class.java.getResource("/landing/index.html")?.readText()
                ?: error("landing/index.html is missing from the backend's resources")
        }

        private const val SECURITY_WARNING = """<p class="warning"><strong>Security is off</strong> (no
      <code>security.jwt.secret</code> in <code>config/application.toml</code>): the remote views sign in, but the
      native web and Compose web clients sign in through <code>/api/auth</code>, which is not there.</p>"""
    }

    /** The page, ready to send. */
    val html: String = CLIENTS.fold(TEMPLATE) { page, client ->
        page.replace("{{$client}}", if (client in available) "available" else "missing")
    }.replace("{{security-warning}}", if (securityOn) "" else SECURITY_WARNING)
}
