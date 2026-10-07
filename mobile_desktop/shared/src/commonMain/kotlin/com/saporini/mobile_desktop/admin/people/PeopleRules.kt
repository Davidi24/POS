package com.saporini.mobile_desktop.admin.people

// The same limits the server checks, so a form can point at the field before anything is sent.

const val MAX_EMAIL = 150
const val MIN_USERNAME = 3
const val MAX_USERNAME = 50
const val MIN_PASSWORD = 8
const val MAX_PASSWORD = 100
const val MAX_PERSON_NAME = 50
const val MAX_PHONE = 30
const val MAX_ROLES_PER_PERSON = 50
const val MAX_ROLE_NAME = 100
const val MAX_ROLE_DESCRIPTION = 1000
const val SUPER_ADMIN = "SUPER_ADMIN"

enum class StaffField { EMAIL, USERNAME, PASSWORD, FIRST_NAME, LAST_NAME, PHONE, ROLES }

/** A person being added ([id] null) or edited. Text is kept as typed; it is trimmed when saved. */
data class StaffDraft(
    val id: String? = null,
    val email: String = "",
    val username: String = "",
    val temporaryPassword: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val phone: String = "",
    val active: Boolean = true,
    val roleIds: Set<String> = emptySet(),
    val defaultBranchId: String? = null
) {
    val isNew: Boolean get() = id == null
}

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
private val USERNAME = Regex("^[A-Za-z0-9._-]+$")
private val PHONE = Regex("^[0-9+()./\\s-]*$")

/** What is wrong with each field, empty when the person can be saved. Email, username and password only matter when adding. */
fun staffProblems(draft: StaffDraft): Map<StaffField, String> = buildMap {
    if (draft.isNew) {
        val email = draft.email.trim()
        when {
            email.isEmpty() -> put(StaffField.EMAIL, "Enter an email")
            email.length > MAX_EMAIL -> put(StaffField.EMAIL, "At most $MAX_EMAIL characters")
            !EMAIL.matches(email) -> put(StaffField.EMAIL, "This isn't an email address")
        }
        val username = draft.username.trim()
        when {
            username.length < MIN_USERNAME -> put(StaffField.USERNAME, "At least $MIN_USERNAME characters")
            username.length > MAX_USERNAME -> put(StaffField.USERNAME, "At most $MAX_USERNAME characters")
            !USERNAME.matches(username) -> put(StaffField.USERNAME, "Use letters, numbers, dots, dashes or underscores")
        }
        // Passwords are never trimmed: spaces count.
        when {
            draft.temporaryPassword.length < MIN_PASSWORD -> put(StaffField.PASSWORD, "At least $MIN_PASSWORD characters")
            draft.temporaryPassword.length > MAX_PASSWORD -> put(StaffField.PASSWORD, "At most $MAX_PASSWORD characters")
        }
    }
    nameProblem(draft.firstName, "first name")?.let { put(StaffField.FIRST_NAME, it) }
    nameProblem(draft.lastName, "last name")?.let { put(StaffField.LAST_NAME, it) }
    val phone = draft.phone.trim()
    when {
        phone.length > MAX_PHONE -> put(StaffField.PHONE, "At most $MAX_PHONE characters")
        !PHONE.matches(phone) -> put(StaffField.PHONE, "Use digits, spaces and + ( ) - only")
    }
    when {
        draft.roleIds.isEmpty() -> put(StaffField.ROLES, "Choose at least one role")
        draft.roleIds.size > MAX_ROLES_PER_PERSON -> put(StaffField.ROLES, "At most $MAX_ROLES_PER_PERSON roles")
    }
}

private fun nameProblem(value: String, what: String): String? {
    val name = value.trim()
    return when {
        name.isEmpty() -> "Enter a $what"
        name.length > MAX_PERSON_NAME -> "At most $MAX_PERSON_NAME characters"
        else -> null
    }
}

/** The problem with a role's name and description, or null. */
fun roleProblem(name: String, description: String): String? {
    val clean = name.trim()
    return when {
        clean.isEmpty() -> "Enter a role name"
        clean.length > MAX_ROLE_NAME -> "The name can be at most $MAX_ROLE_NAME characters"
        description.trim().length > MAX_ROLE_DESCRIPTION -> "The description can be at most $MAX_ROLE_DESCRIPTION characters"
        else -> null
    }
}

/** A permission in the role editor, with whether the signed-in person may give or take it away. */
data class PermissionChoice(val permission: PermissionDto, val granted: Boolean, val grantable: Boolean)

/** Permissions grouped by area ("ORDER_READ" → "ORDER"), in a stable order for the editor. */
fun permissionGroups(choices: List<PermissionChoice>): List<Pair<String, List<PermissionChoice>>> =
    choices.sortedBy { it.permission.code }
        .groupBy { it.permission.code.substringBefore('_') }
        .toList()

/**
 * Whether [actorPermissions] may save [granted] on a role: the server refuses any set holding a permission the person
 * doesn't have themselves (a super admin may give anything).
 */
fun canSavePermissions(granted: Set<String>, actorPermissions: Set<String>, superAdmin: Boolean): Boolean =
    superAdmin || actorPermissions.containsAll(granted)
