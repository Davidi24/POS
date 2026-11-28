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

/**
 * The platform's own roles. Nothing about them ever shows inside a restaurant: no card, no choice when adding someone
 * (user rule, 2026-10-30: "nobody there knows anything about the super admin, not even that it exists").
 */
val PlatformRoleCodes = setOf(SUPER_ADMIN, "CO_SUPER_ADMIN")

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


/**
 * Permissions that only make sense with another one (user, 2026-11-19: "someone can't have View POS off and edit the POS
 * on"), from the server's catalog (`parentCode`): a workspace's "Open …", then each part's "View …", then what changes
 * things. Switching one on also switches on what it needs; switching one off also switches off what needs it.
 */
class PermissionTree(catalog: List<PermissionDto>) {
    private val parent: Map<String, String> = catalog.mapNotNull { p -> p.parentCode?.let { p.code to it } }.toMap()
    private val children: Map<String, List<String>> = parent.entries.groupBy({ it.value }, { it.key })

    fun parentOf(code: String): String? = parent[code]

    /** What [code] needs, nearest first. */
    fun ancestors(code: String): List<String> = generateSequence(parent[code]) { parent[it] }.take(10).toList()

    /** Everything that needs [code], however far down. */
    fun descendants(code: String): Set<String> = buildSet {
        val queue = ArrayDeque(children[code].orEmpty())
        while (queue.isNotEmpty()) {
            val next = queue.removeFirst()
            if (add(next)) queue.addAll(children[next].orEmpty())
        }
    }

    /** [codes] that count: only those whose every ancestor is in [codes] too. */
    fun shown(codes: Set<String>): Set<String> = codes.filterTo(LinkedHashSet()) { code -> ancestors(code).all { it in codes } }

    /**
     * [current] with [codes] switched [on]: on brings what they need (only when [allowed] lets every one of them be on),
     * off takes away what needs them.
     */
    fun switch(current: Set<String>, codes: Collection<String>, on: Boolean, allowed: (String) -> Boolean): Set<String> =
        switchRemembering(current, codes, on, allowed, emptyMap()).codes

    /** What switching gives: the permissions now on, and what each switched-off one had on under it. */
    data class Switched(val codes: Set<String>, val remembered: Map<String, Set<String>>)

    /**
     * Like [switch], but remembers what was on under a permission switched off and brings it back when it's switched on
     * again (user, 2026-11-20: "if it's turned off and on it remembers the permissions"), as long as they may be given.
     * Bundled permissions (starting and cancelling an order with "Edit orders") follow theirs.
     */
    fun switchRemembering(current: Set<String>, codes: Collection<String>, on: Boolean, allowed: (String) -> Boolean,
                          remembered: Map<String, Set<String>>): Switched {
        val result = LinkedHashSet(current)
        val memory = remembered.toMutableMap()
        if (on) {
            codes.forEach { code ->
                val needed = listOf(code) + ancestors(code)
                if (needed.all { it in result || allowed(it) }) {
                    result.addAll(needed)
                    memory.remove(code)?.filter { allowed(it) || it in current }?.let(result::addAll)
                }
            }
        } else {
            codes.forEach { code ->
                val under = descendants(code).filterTo(LinkedHashSet()) { it in result }
                if (under.isNotEmpty()) memory[code] = under
                result.remove(code); result.removeAll(under)
            }
        }
        bundled.forEach { (code, owner) -> if (owner in result) result.add(code) else result.remove(code) }
        return Switched(result, memory)
    }

    private val bundled: Map<String, String> = catalog.mapNotNull { p -> p.bundledInto?.let { p.code to it } }.toMap()
}
