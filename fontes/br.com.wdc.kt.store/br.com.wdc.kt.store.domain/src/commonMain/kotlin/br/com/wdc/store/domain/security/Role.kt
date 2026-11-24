package br.com.wdc.store.domain.security

/**
 * The access roles of the WDC Store.
 *
 * A role carries no permissions of its own: what each one may do with the data, through the REST API, is declared by
 * the data policies of the REST layer (`UserDataPolicy`, `PurchaseDataPolicy`, …), by the role name. A user may have
 * several roles; the model is *allow-wins*.
 */
enum class Role {
    ADMIN,
    CUSTOMER,
    MANAGER;

    companion object {

        /** The names of the roles, as a [SecurityContext] carries them. */
        fun names(roles: Set<Role>): Set<String> = roles.mapTo(LinkedHashSet()) { it.name }

        /** Parses comma-separated roles (`"ADMIN,CUSTOMER"`); unknown names are ignored. */
        fun parse(rolesStr: String?): Set<Role> {
            if (rolesStr.isNullOrBlank()) return emptySet()
            return rolesStr.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { name -> entries.firstOrNull { it.name == name } }
                .toSet()
        }
    }
}
