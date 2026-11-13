package com.saporini.mobile_desktop.platform

import com.saporini.mobile_desktop.core.network.ApiException
import kotlinx.datetime.TimeZone

// Field limits (the server's), the choices for currency and time zone, and the messages of the super admins' screens.

const val MAX_RESTAURANT_NAME = 150
const val MAX_LEGAL_NAME = 200
const val MAX_DESCRIPTION = 2000
const val MAX_EMAIL_TEXT = 150
const val MAX_WEBSITE = 255
const val MAX_BRANCH_NAME = 150
const val MAX_CODE = 100
const val MAX_CITY = 100
const val MAX_STREET = 255
const val MAX_POSTAL = 20
const val MAX_TAX_FIELD = 100
const val MAX_RECEIPT_TEXT = 2000

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
private val PHONE = Regex("^\\+?[0-9\\s\\-().]{7,50}$")
private val HEX_COLOR = Regex("^#([0-9a-fA-F]{3}){1,2}$")
private val USERNAME = Regex("^[A-Za-z0-9._-]{3,50}$")

fun emailProblem(value: String, required: Boolean = false): String? = when {
    value.isBlank() -> if (required) "Enter an email" else null
    value.trim().length > MAX_EMAIL_TEXT -> "At most $MAX_EMAIL_TEXT characters"
    !EMAIL.matches(value.trim()) -> "This isn't an email address"
    else -> null
}

fun phoneProblem(value: String): String? = when {
    value.isBlank() -> null
    !PHONE.matches(value.trim()) -> "Use digits, spaces and + ( ) - only (7 or more)"
    else -> null
}

fun colorProblem(value: String): String? = if (value.isBlank() || HEX_COLOR.matches(value.trim())) null else "Use a colour like #4F7942"

fun usernameProblem(value: String): String? = when {
    value.isBlank() -> "Enter a username"
    !USERNAME.matches(value.trim()) -> "3–50 letters, numbers, dots, dashes or underscores"
    else -> null
}

fun lengthProblem(value: String, max: Int, required: Boolean = false, what: String = "this"): String? = when {
    value.isBlank() -> if (required) "Enter $what" else null
    value.trim().length > max -> "At most $max characters"
    else -> null
}

/** A picture picked on this device and checked (PNG, JPEG or WebP under 5 MB), waiting to be uploaded. */
class PickedPicture(val bytes: ByteArray, val fileName: String, val contentType: String)

/** A restaurant's owner being added; they get an email to set a password. */
data class OwnerDraft(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    val username: String = "",
    // True once the username was typed by hand, so it stops following the email.
    val usernameTouched: Boolean = false,
    val showProblems: Boolean = false
) {
    /** Applies [change]; the username follows the email until it's typed by hand. */
    fun edit(change: (OwnerDraft) -> OwnerDraft): OwnerDraft {
        val next = change(this)
        return if (next.email != email && !next.usernameTouched) next.copy(username = usernameFrom(next.email)) else next
    }

    fun request() = OwnerInviteDto(email.trim(), username.trim(), firstName.trim(), lastName.trim(), phone.trim().ifBlank { null })
}

fun ownerProblems(draft: OwnerDraft): Map<String, String> = buildMap {
    lengthProblem(draft.firstName, 50, true, "a first name")?.let { put("firstName", it) }
    lengthProblem(draft.lastName, 50, true, "a last name")?.let { put("lastName", it) }
    emailProblem(draft.email, required = true)?.let { put("email", it) }
    usernameProblem(draft.username)?.let { put("username", it) }
    if (draft.phone.trim().length > 30) put("phone", "At most 30 characters")
}

/** Adding an owner to [restaurantId]; [afterCreate] when it's the second step of creating the restaurant. */
data class OwnerStep(
    val restaurantId: String,
    val restaurantName: String,
    val draft: OwnerDraft = OwnerDraft(),
    val afterCreate: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null
)

/** Adding a branch to [restaurantId] from the restaurant list or the "Choose a branch" page. */
data class BranchStep(
    val restaurantId: String,
    val restaurantName: String,
    val draft: BranchDraft = BranchDraft(),
    val saving: Boolean = false,
    val error: String? = null
)

/** Creates the branch, then its address when one was typed. */
suspend fun PlatformRepository.createBranchWithAddress(restaurantId: String, draft: BranchDraft): BranchDto {
    val branch = createBranch(restaurantId, CreateBranchRequestDto(draft.name.trim(), draft.code.trim().ifBlank { null },
        draft.description.trim().ifBlank { null }, draft.email.trim().ifBlank { null }, draft.phone.trim().ifBlank { null }))
    if (draft.street.isNotBlank()) {
        saveAddress(restaurantId, branch.id, null, AddressRequestDto("PHYSICAL", draft.country.trim(), draft.city.trim(),
            draft.postalCode.trim().ifBlank { null }, draft.street.trim(), null, true))
    }
    return branch
}

/** A username suggestion from an email: "anna.k@x.com" → "anna.k". */
fun usernameFrom(email: String): String =
    email.substringBefore('@').filter { it.isLetterOrDigit() || it in "._-" }.take(50)

/** The server's message for a refused change (it explains what to do), or a general one. */
internal fun platformMessage(error: Exception, write: Boolean): String = when {
    error is ApiException && error.status == 401 -> "Sign in again to continue."
    error is ApiException && error.status == 404 -> "This no longer exists. It may have been removed on another device."
    error is ApiException && error.status == 413 -> "This is too large."
    error is ApiException && error.status in 400..499 -> error.message.ifBlank { "This can't be saved." }
    error is ApiException && write -> "The server had a problem. Refresh to see whether the change was saved."
    error is ApiException -> "The server had a problem. Try again."
    write -> "No answer from the server. Refresh to see whether the change was saved."
    else -> "Could not load. Check the connection and try again."
}

data class CurrencyChoice(val code: String, val name: String) {
    val label: String get() = "$code · $name"
}

/** Currencies restaurants are likely to use (any ISO code can still be typed in the currency change). */
val CURRENCIES = listOf(
    CurrencyChoice("EUR", "Euro"), CurrencyChoice("ALL", "Albanian lek"), CurrencyChoice("USD", "US dollar"),
    CurrencyChoice("GBP", "British pound"), CurrencyChoice("CHF", "Swiss franc"), CurrencyChoice("MKD", "Macedonian denar"),
    CurrencyChoice("RSD", "Serbian dinar"), CurrencyChoice("BAM", "Bosnia-Herzegovina mark"), CurrencyChoice("HUF", "Hungarian forint"),
    CurrencyChoice("CZK", "Czech koruna"), CurrencyChoice("PLN", "Polish złoty"), CurrencyChoice("RON", "Romanian leu"),
    CurrencyChoice("BGN", "Bulgarian lev"), CurrencyChoice("TRY", "Turkish lira"), CurrencyChoice("SEK", "Swedish krona"),
    CurrencyChoice("NOK", "Norwegian krone"), CurrencyChoice("DKK", "Danish krone"), CurrencyChoice("ISK", "Icelandic króna"),
    CurrencyChoice("UAH", "Ukrainian hryvnia"), CurrencyChoice("MDL", "Moldovan leu"), CurrencyChoice("GEL", "Georgian lari"),
    CurrencyChoice("AED", "UAE dirham"), CurrencyChoice("SAR", "Saudi riyal"), CurrencyChoice("QAR", "Qatari riyal"),
    CurrencyChoice("ILS", "Israeli shekel"), CurrencyChoice("EGP", "Egyptian pound"), CurrencyChoice("MAD", "Moroccan dirham"),
    CurrencyChoice("ZAR", "South African rand"), CurrencyChoice("CAD", "Canadian dollar"), CurrencyChoice("MXN", "Mexican peso"),
    CurrencyChoice("BRL", "Brazilian real"), CurrencyChoice("ARS", "Argentine peso"), CurrencyChoice("AUD", "Australian dollar"),
    CurrencyChoice("NZD", "New Zealand dollar"), CurrencyChoice("JPY", "Japanese yen"), CurrencyChoice("CNY", "Chinese yuan"),
    CurrencyChoice("HKD", "Hong Kong dollar"), CurrencyChoice("SGD", "Singapore dollar"), CurrencyChoice("INR", "Indian rupee"),
    CurrencyChoice("THB", "Thai baht"), CurrencyChoice("KRW", "South Korean won"), CurrencyChoice("IDR", "Indonesian rupiah")
)

fun currencyName(code: String?): String = CURRENCIES.firstOrNull { it.code == code }?.name ?: code.orEmpty()

/** Every IANA time zone the device knows ("Europe/Tirane"), sorted, without the old aliases. */
val TIME_ZONES: List<String> by lazy {
    TimeZone.availableZoneIds
        .filter { it.contains('/') && !it.startsWith("Etc/") && !it.startsWith("SystemV/") && it.first().isUpperCase() }
        .sorted()
}

/** "Europe/Tirane" → "Tirane (Europe)". */
fun timeZoneLabel(zone: String): String {
    val city = zone.substringAfterLast('/').replace('_', ' ')
    val area = zone.substringBefore('/')
    return if (area == zone) zone else "$city ($area)"
}

/** "ACTIVE" + active → "Open"; the other states in plain words. */
fun restaurantStatusLabel(status: String, active: Boolean): String = when {
    status == "ACTIVE" && active -> "Open"
    status == "SUSPENDED" -> "Suspended"
    status == "INACTIVE" || !active -> "Closed"
    status == "ARCHIVED" -> "Removed"
    else -> status.lowercase().replaceFirstChar { it.uppercase() }
}

fun branchStatusLabel(status: String, active: Boolean): String = when {
    status == "ACTIVE" && active -> "Open"
    status == "TEMPORARILY_CLOSED" -> "Closed for now"
    status == "INACTIVE" -> "Closed"
    status == "ARCHIVED" -> "Removed"
    else -> status.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

fun addressTypeLabel(type: String): String = when (type) {
    "PHYSICAL" -> "Where guests find it"
    "LEGAL" -> "Registered (legal) address"
    "BILLING" -> "Billing address"
    "HEAD_OFFICE" -> "Head office"
    "SHIPPING" -> "Deliveries"
    else -> type
}

fun contactTypeLabel(type: String): String = when (type) {
    "GENERAL" -> "General"
    "OWNER" -> "Owner"
    "MANAGER" -> "Manager"
    "ACCOUNTING" -> "Accounting"
    "SUPPORT" -> "Support"
    else -> type
}

/** Which area of the app a permission belongs to, for the Co-Super-Admin ticks. */
fun permissionArea(code: String): String = when {
    code.endsWith("_ACCESS") -> "Apps they can open"
    code.startsWith("RESTAURANTS_") -> "Restaurants"
    code.startsWith("USERS_") || code.startsWith("ROLES_") || code == "SESSIONS_MANAGE" -> "Staff and roles"
    code.startsWith("SHIFT_") -> "Shifts and pay"
    code.startsWith("MENUS_") -> "Menus"
    code.startsWith("ORDER_") -> "Orders"
    code.startsWith("PAYMENT_") -> "Payments and refunds"
    code.startsWith("RESERVATION_") -> "Bookings"
    code.startsWith("KDS_") -> "Kitchen display"
    code.startsWith("SETTINGS_") -> "Settings"
    code.startsWith("REPORTS_") || code.startsWith("FRAUD_") -> "Statistics and fraud"
    else -> "Other"
}

val PERMISSION_AREAS = listOf(
    "Restaurants", "Apps they can open", "Staff and roles", "Settings", "Menus", "Orders", "Payments and refunds",
    "Bookings", "Kitchen display", "Shifts and pay", "Statistics and fraud", "Other"
)

// This application supports ISO currencies with at most two decimal places.
val ZERO_DECIMAL_CURRENCIES = setOf("BIF", "CLP", "DJF", "GNF", "ISK", "JPY", "KMF", "KRW", "PYG", "RWF", "UGX", "UYI", "VND", "VUV", "XAF", "XOF", "XPF")
fun taxInstant(value: String): kotlin.time.Instant? = runCatching { kotlin.time.Instant.parse(value.trim()) }.getOrNull()
fun TaxProfileDto.effectiveAt(now: kotlin.time.Instant): Boolean =
    (effectiveFrom == null || taxInstant(effectiveFrom)?.let { now >= it } == true) &&
    (effectiveTo == null || taxInstant(effectiveTo)?.let { now < it } == true)
fun websiteProblem(value: String): String? {
    lengthProblem(value, MAX_WEBSITE)?.let { return it }
    val text=value.trim()
    if (text.isEmpty()) return null
    if (!Regex("(?i)^https?://[^\\s]+$").matches(text)) return "Enter a complete http:// or https:// website address"
    val url=runCatching { io.ktor.http.Url(text) }.getOrNull()
    return if (url == null || url.host.isBlank() || url.user != null || url.password != null || url.port !in 1..65535)
        "Enter a valid website address without login details" else null
}
