package com.swiftq.staff.data.remote

object StaffRoleValidator {

    private val allowedRoles = setOf(
        "receptionist",
        "nurse",
        "manager",
        "it_admin"
    )

    fun isAllowed(role: String): Boolean {
        return role.trim().lowercase() in allowedRoles
    }
}