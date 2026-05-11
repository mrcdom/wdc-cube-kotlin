package br.com.wdc.shopping.domain.security

enum class Role(val permissions: Set<String>) {

    ADMIN(setOf(
        "user:*",
        "product:*",
        "purchase:*",
        "purchase-item:*",
        "data:all",
    )),

    CUSTOMER(setOf(
        "product:read",
        "purchase:read", "purchase:write",
        "purchase-item:read", "purchase-item:write",
    )),

    MANAGER(setOf(
        "product:read", "product:write",
        "purchase:read",
        "purchase-item:read",
    ));

    companion object {

        fun effectivePermissions(roles: Set<Role>): Set<String> =
            roles.flatMapTo(mutableSetOf()) { it.permissions }

        fun parse(rolesStr: String?): Set<Role> {
            if (rolesStr.isNullOrBlank()) return emptySet()
            return rolesStr.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull {
                    try { valueOf(it) }
                    catch (_: IllegalArgumentException) { null }
                }
                .toSet()
        }

        fun hasPermission(permissions: Set<String>, entity: String, operation: String): Boolean =
            permissions.contains("$entity:$operation") || permissions.contains("$entity:*")

        fun hasDataAll(permissions: Set<String>): Boolean =
            permissions.contains("data:all")
    }
}
