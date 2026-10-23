package br.com.wdc.framework.rest.security

import br.com.wdc.framework.domain.exception.AccessDeniedException
import br.com.wdc.framework.rest.RestApi
import br.com.wdc.framework.rest.error.ErrorResponse
import br.com.wdc.framework.rest.testing.RestTestServer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A segurança no formato do backend Spring a substituir: 403 sem credencial, papéis `ROLE_`, `@PreAuthorize`. */
class SecurityTest {

    class User(override val id: String, override val roles: Set<String>, val companyId: Long) : RestPrincipal

    private val users = mapOf(
        "t-admin" to User("1", setOf("ROLE_ADMIN"), 10),
        "t-patient" to User("2", setOf("ROLE_PATIENT"), 20),
    )

    private val roles = RoleRules("ROLE_")

    private val api = RestApi {
        security {
            authenticator = BearerAuthenticator { token -> users[token] }
            unauthenticatedStatus = 403
            protectUnmatched = true
            permitAll("POST /auth/sign_in", "GET /company/active", "/swagger-ui/**")
        }
        errors {
            // o corpo padrão do Spring Boot para quem não tem credencial…
            errorBody = { status, _, request ->
                linkedMapOf("status" to status, "error" to if (status == 403) "Forbidden" else "Not Found", "path" to request.path)
            }
            // …e o do @RestControllerAdvice para a recusa do @PreAuthorize
            on<AccessDeniedException> { _, _ -> ErrorResponse(403, mapOf("message" to "Acesso negado.")) }
        }
        post("/auth/sign_in") { call -> mapOf("principal" to call.principal?.id) }
        get("/company/active") { call -> mapOf("principal" to call.principal?.id) }
        get("/swagger-ui/index.html") { _ -> "docs" }
        get("/me") { call -> mapOf("id" to call.requirePrincipal<User>().id) }
        get("/admin", rule = roles.hasRole("ADMIN")) { _ -> "admin" }
        get("/admin-prefixed", rule = roles.hasAnyRole("ROLE_ADMIN", "ROLE_COMPANY_ADMIN")) { _ -> "admin" }
        get("/patients", rule = roles.hasAnyRole("PATIENT", "ADMIN")) { _ -> "ok" }
        get(
            "/company/{id}",
            rule = AccessRules.principalOf<User> { it.hasRole("ROLE_ADMIN") } or
                AccessRules.rule { r -> (r.principal as? User)?.companyId == r.pathParam("id")?.toLongOrNull() },
        ) { call -> call.pathLong("id") }
        get("/open", public = true) { _ -> "open" }
    }

    private val server = RestTestServer(api)

    @AfterTest
    fun stop() = server.close()

    private fun anonymous() = server.client()

    private fun admin() = server.client().bearer("t-admin")

    private fun patient() = server.client().bearer("t-patient")

    @Test
    fun withoutCredential_theConfiguredStatus_withTheDefaultErrorBody() {
        val response = anonymous().get("/me")
        assertEquals(403, response.status)
        assertEquals("""{"status":403,"error":"Forbidden","path":"/me"}""", response.body)
    }

    @Test
    fun invalidToken_isTreatedAsNoCredential() {
        assertEquals(403, server.client().bearer("bogus").get("/me").status)
        assertEquals(403, server.client().header("Authorization", "Basic abc").get("/me").status)
    }

    @Test
    fun ruleRefusal_usesTheAccessDeniedMapping() {
        val response = patient().get("/admin")
        assertEquals(403, response.status)
        assertEquals("""{"message":"Acesso negado."}""", response.body)
        assertEquals("admin", admin().get("/admin").body)
    }

    @Test
    fun roleRules_acceptBothFormsOfTheRole() {
        assertEquals(200, admin().get("/admin-prefixed").status)
        assertEquals(200, patient().get("/patients").status)
        assertEquals(200, admin().get("/patients").status)
        assertFalse(AccessRules.hasRole("ADMIN").allows(fakeRequestless()), "sem prefixo, comparação exata")
    }

    @Test
    fun ruleOverPathParameters() {
        assertEquals("20", patient().get("/company/20").body)
        assertEquals(403, patient().get("/company/10").status)
        assertEquals("10", admin().get("/company/10").body)
    }

    @Test
    fun publicRoutes_areOpen_butStillSeeTheCaller() {
        assertEquals("""{}""", anonymous().post("/auth/sign_in", "{}").body)
        assertEquals("""{"principal":"1"}""", admin().get("/company/active").body)
        assertEquals("docs", anonymous().get("/swagger-ui/index.html").body)
        assertEquals("open", anonymous().get("/open").body)
        // credencial recusada numa rota pública: segue anônimo
        assertEquals("""{}""", server.client().bearer("bogus").get("/company/active").body)
        // o verbo conta
        assertEquals(403, anonymous().send("GET", "/auth/sign_in").status)
    }

    @Test
    fun unmatchedPath_anonymousGetsTheUnauthenticatedResponse_authenticatedGets404() {
        assertEquals(403, anonymous().get("/no/such/route").status)
        val found = admin().get("/no/such/route")
        assertEquals(404, found.status)
        assertEquals("""{"status":404,"error":"Not Found","path":"/no/such/route"}""", found.body)
    }

    @Test
    fun requestMatcher_patterns() {
        val m = RequestMatcher.of("/measurement_history/**")
        assertTrue(m.matches("GET", "/measurement_history"))
        assertTrue(m.matches("POST", "/measurement_history/1/2"))
        assertFalse(m.matches("GET", "/measurement_historyx"))
        val single = RequestMatcher.of("GET /user/{id}/photo*")
        assertTrue(single.matches("get", "/user/5/photo.png"))
        assertTrue(single.matches("GET", "/user/5/photo/"))
        assertFalse(single.matches("GET", "/user/5/6/photo"))
        assertFalse(single.matches("POST", "/user/5/photo"))
    }

    private fun fakeRequestless(): br.com.wdc.framework.rest.RequestContext {
        var captured: br.com.wdc.framework.rest.RequestContext? = null
        RestTestServer(RestApi { get("/x") { call -> captured = call.request; null } }).use { it.client().get("/x") }
        assertNull(captured!!.principal)
        captured!!.principal = User("9", setOf("ROLE_ADMIN"), 1)
        return captured!!
    }
}
