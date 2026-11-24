package br.com.wdc.store.test

import br.com.wdc.store.domain.security.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The roles a user record carries, and the names a security context gets — what the REST data policies grant by. */
class RoleTest {

    @Test
    fun parse_readsCommaSeparatedNames_ignoringBlanksAndUnknownOnes() {
        assertEquals(setOf(Role.ADMIN, Role.MANAGER), Role.parse(" ADMIN , MANAGER,,SUPERUSER"))
        assertEquals(emptySet<Role>(), Role.parse(null))
        assertEquals(emptySet<Role>(), Role.parse("  "))
        // case matters: the names are the enum's
        assertEquals(emptySet<Role>(), Role.parse("admin"))
    }

    @Test
    fun names_areWhatTheSecurityContextCarries() {
        assertEquals(setOf("CUSTOMER", "MANAGER"), Role.names(Role.parse("CUSTOMER,MANAGER")))
    }
}
