package com.saporini.mobile_desktop.platform

import com.saporini.mobile_desktop.core.network.ApiConfig
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimalSerializer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.Serializable

// The super admins' side of the app: the restaurant list, the Restaurant workspace (details, branches, addresses,
// contacts, logo and receipts, legal and tax details, currency and time zone) and the Co-Super-Admins' permissions.
// The owner's "Restaurant info" in the Admin Hub uses the same calls for what an owner may change.

val RESTAURANT_OPEN_STATUSES = listOf("ACTIVE")
val ADDRESS_TYPES = listOf("PHYSICAL", "LEGAL", "BILLING", "HEAD_OFFICE", "SHIPPING")
val CONTACT_TYPES = listOf("GENERAL", "OWNER", "MANAGER", "ACCOUNTING", "SUPPORT")
/** The server's rounding steps for converted prices. */
val ROUNDING_STEPS = listOf("0.01", "0.05", "0.10", "0.50", "1", "5", "10", "50", "100")

@Serializable
data class PageDto<T>(
    val items: List<T> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    val hasNext: Boolean = false
)

@Serializable
data class RestaurantCardDto(
    val id: String,
    val name: String,
    val code: String = "",
    val status: String = "ACTIVE",
    val active: Boolean = true,
    val currency: String = "",
    val timezone: String = "",
    val city: String? = null,
    val country: String? = null,
    val branches: Long = 0,
    val openBranches: Long = 0,
    val staff: Long = 0,
    val ownerUserId: String? = null,
    val ownerName: String? = null,
    val ownerEmail: String? = null,
    val ownerInvitePending: Boolean = false,
    // The latest invitation email: PENDING/SENDING, SENT, FAILED or CANCELLED (null: none on record).
    val ownerInviteDelivery: String? = null,
    val ownerInviteExpired: Boolean = false,
    val logoUrl: String? = null,
    val coverUrl: String? = null,
    val createdAt: String? = null
) {
    val open: Boolean get() = active && status == "ACTIVE"
}

/** Whether the owner's invitation email went out (or waits for a retry, or failed) and whether its link still works. */
@Serializable
data class OwnerInviteStatusDto(
    val ownerUserId: String,
    val email: String = "",
    val signedUp: Boolean = false,
    val delivery: String? = null,
    val attempts: Int = 0,
    val queuedAt: String? = null,
    val sentAt: String? = null,
    val nextAttemptAt: String? = null,
    val lastError: String? = null,
    val linkStatus: String? = null,
    val linkExpiresAt: String? = null
)

@Serializable
data class RestaurantDto(
    val id: String,
    val name: String,
    val legalName: String = "",
    val code: String = "",
    val slug: String = "",
    val description: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val website: String? = null,
    val currency: String = "",
    val timezone: String = "",
    val isActive: Boolean = true,
    val status: String = "ACTIVE",
    val ownerUserId: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val version: Long = 0
) {
    val open: Boolean get() = isActive && status == "ACTIVE"
}

/** The whole restaurant as PUT expects it (fields left null are cleared). */
@Serializable
data class UpdateRestaurantRequestDto(
    val name: String,
    val legalName: String,
    val code: String,
    val slug: String,
    val description: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val website: String? = null,
    val currency: String,
    val timezone: String,
    val ownerUserId: String? = null,
    val isActive: Boolean,
    val status: String,
    val expectedVersion: Long
)

@Serializable
data class StatusRequestDto(val isActive: Boolean, val status: String)

@Serializable
data class RestaurantOrderRequestDto(val restaurantIds: List<String>)

@Serializable
data class OwnerInviteDto(
    val email: String,
    val username: String,
    val firstName: String,
    val lastName: String,
    val phone: String? = null
)

@Serializable
data class UpdateOwnerRequestDto(val firstName: String, val lastName: String, val email: String, val phone: String? = null)

@Serializable
data class CreateRestaurantRequestDto(
    val name: String,
    val legalName: String,
    val description: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val website: String? = null,
    val currency: String,
    val timezone: String,
    // Left out: the owner is added in a second step (createOwner).
    val owner: OwnerInviteDto? = null,
    val firstBranchName: String? = null
)

@Serializable
data class BranchDto(
    val id: String,
    val name: String,
    val code: String = "",
    val description: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val coverUrl: String? = null,
    val isActive: Boolean = true,
    val status: String = "ACTIVE",
    val managerUserId: String? = null,
    val createdAt: String? = null,
    val version: Long = 0
) {
    val open: Boolean get() = isActive && status == "ACTIVE"
}

/** One branch on the "Choose a branch" page. */
@Serializable
data class BranchCardDto(
    val id: String,
    val name: String,
    val code: String = "",
    val status: String = "ACTIVE",
    val active: Boolean = true,
    val coverUrl: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val street: String? = null,
    val city: String? = null,
    val country: String? = null,
    val staff: Long = 0,
    val devices: Long = 0
) {
    val open: Boolean get() = active && status == "ACTIVE"
}

/** One page of the "Choose a branch" cards; [allBranches] and [openBranches] count the whole restaurant. */
@Serializable
data class BranchCardPageDto(
    val items: List<BranchCardDto> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    val hasNext: Boolean = false,
    val allBranches: Long = 0,
    val openBranches: Long = 0
)

@Serializable
data class CoverDto(val coverUrl: String? = null)

// ---- Owner transfer ----

/** Someone already in the POS who can be given the restaurant (active staff of it). */
@Serializable
data class OwnerCandidateDto(
    val id: String,
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val username: String = "",
    val roles: List<String> = emptyList()
) {
    val displayName: String get() = "$firstName $lastName".trim().ifBlank { username }
}

/** Exactly one of [newOwnerUserId] and [newOwner]; [expectedVersion] is the restaurant's version that was looked at. */
@Serializable
data class OwnerTransferRequestDto(
    val newOwnerUserId: String? = null,
    val newOwner: OwnerInviteDto? = null,
    val expectedVersion: Long,
    val reason: String? = null
)

/** One change of a restaurant's owner. */
@Serializable
data class OwnerTransferDto(
    val id: String,
    val previousOwnerId: String? = null,
    val previousOwnerName: String? = null,
    val newOwnerId: String,
    val newOwnerName: String? = null,
    val newOwnerInvited: Boolean = false,
    val previousOwnerDeactivated: Boolean = false,
    val sessionsRevoked: Int = 0,
    val reason: String? = null,
    val transferredByName: String? = null,
    val transferredAt: String? = null
)

// ---- Restaurant applications (reviewed by super admins) ----

@Serializable
data class ApplicantDto(
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val username: String? = null,
    val phone: String? = null
) {
    val displayName: String get() = listOfNotNull(firstName, lastName).joinToString(" ").ifBlank { username.orEmpty() }
}

/** A public application to open a restaurant: PENDING, APPROVED or REJECTED. */
@Serializable
data class RestaurantApplicationDto(
    val id: String,
    val name: String,
    val legalName: String = "",
    val description: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val website: String? = null,
    val currency: String = "",
    val timezone: String = "",
    val status: String = "PENDING",
    val rejectionReason: String? = null,
    val submittedAt: String? = null,
    val reviewedAt: String? = null,
    val owner: ApplicantDto? = null
)

@Serializable
data class ReviewApplicationRequestDto(val decision: String, val rejectionReason: String? = null)

@Serializable
data class CreateBranchRequestDto(
    val name: String,
    val code: String? = null,
    val description: String? = null,
    val email: String? = null,
    val phone: String? = null
)

@Serializable
data class UpdateBranchRequestDto(
    val name: String,
    val code: String,
    val description: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val managerUserId: String? = null,
    val isActive: Boolean,
    val status: String,
    val expectedVersion: Long
)

@Serializable
data class BranchRemovalCheckDto(
    val canRemove: Boolean = false,
    val openOrders: Long = 0,
    val upcomingBookings: Long = 0,
    val openShifts: Long = 0,
    val staff: Long = 0,
    val devices: Long = 0,
    val lastBranch: Boolean = false,
    val reasons: List<String> = emptyList(),
    val archive: BranchRemovalPlanDto? = null
)

@Serializable
data class BranchRemovalPlanDto(
    val canRemove: Boolean = false,
    val reasons: List<String> = emptyList(),
    val staff: Long = 0,
    val protectedStaff: Long = 0,
    val devices: Long = 0,
    val tables: Long = 0,
    val kitchenStations: Long = 0,
    val stockLocations: Long = 0,
    val canDeactivateStaff: Boolean = false,
    val expectedVersion: Long = 0,
    val previewToken: String = ""
)

@Serializable
data class BranchRemovalRequestDto(val staffAction: String, val expectedVersion: Long, val confirmedName: String, val previewToken: String)

@Serializable
data class AddressDto(
    val id: String,
    val addressType: String = "PHYSICAL",
    val country: String = "",
    val city: String = "",
    val postalCode: String? = null,
    val streetLine1: String = "",
    val streetLine2: String? = null,
    val isPrimary: Boolean = false
) {
    val oneLine: String
        get() = listOfNotNull(streetLine1.takeIf { it.isNotBlank() }, streetLine2?.takeIf { it.isNotBlank() },
            listOfNotNull(postalCode?.takeIf { it.isNotBlank() }, city.takeIf { it.isNotBlank() }).joinToString(" ").ifBlank { null },
            country.takeIf { it.isNotBlank() }).joinToString(", ")
}

@Serializable
data class AddressRequestDto(
    val addressType: String,
    val country: String,
    val city: String,
    val postalCode: String? = null,
    val streetLine1: String,
    val streetLine2: String? = null,
    val isPrimary: Boolean
)

@Serializable
data class ContactDto(
    val id: String,
    val contactType: String = "GENERAL",
    val fullName: String = "",
    val email: String? = null,
    val phone: String? = null,
    val isPrimary: Boolean = false,
    val jobTitle: String? = null
)

@Serializable
data class ContactRequestDto(
    val contactType: String,
    val fullName: String,
    val email: String? = null,
    val phone: String? = null,
    val isPrimary: Boolean,
    val jobTitle: String? = null
)

@Serializable
data class BrandingDto(
    val id: String? = null,
    val logoUrl: String? = null,
    val primaryColor: String? = null,
    val secondaryColor: String? = null,
    val receiptHeader: String? = null,
    val receiptFooter: String? = null
)

/** The logo is uploaded on its own; leaving it out here keeps it. */
@Serializable
data class BrandingRequestDto(
    val primaryColor: String? = null,
    val secondaryColor: String? = null,
    val receiptHeader: String? = null,
    val receiptFooter: String? = null
)

@Serializable
data class TaxProfileDto(
    val id: String,
    val country: String = "",
    val fiscalCode: String? = null,
    val taxNumber: String? = null,
    val vatNumber: String? = null,
    val taxOffice: String? = null,
    val isDefault: Boolean = false,
    val effectiveFrom: String? = null,
    val effectiveTo: String? = null
)

@Serializable
data class TaxProfileRequestDto(
    val country: String,
    val fiscalCode: String? = null,
    val taxNumber: String? = null,
    val vatNumber: String? = null,
    val taxOffice: String? = null,
    val isDefault: Boolean,
    val effectiveFrom: String? = null,
    val effectiveTo: String? = null
)

// ---- Currency change ----

@Serializable
data class CurrencyPreviewRequestDto(
    val newCurrency: String,
    @Serializable(with = OrderDecimalSerializer::class) val rate: OrderDecimal,
    @Serializable(with = OrderDecimalSerializer::class) val roundingStep: OrderDecimal
)

@Serializable
data class CurrencyOverrideDto(val kind: String, val id: String, @Serializable(with = OrderDecimalSerializer::class) val value: OrderDecimal)

@Serializable
data class CurrencyChangeRequestDto(
    val newCurrency: String,
    @Serializable(with = OrderDecimalSerializer::class) val rate: OrderDecimal,
    @Serializable(with = OrderDecimalSerializer::class) val roundingStep: OrderDecimal,
    val overrides: List<CurrencyOverrideDto>,
    val previewToken: String
)

@Serializable
data class CurrencyItemDto(
    val kind: String,
    val id: String,
    val name: String,
    val detail: String? = null,
    @Serializable(with = OrderDecimalSerializer::class) val oldValue: OrderDecimal,
    @Serializable(with = OrderDecimalSerializer::class) val newValue: OrderDecimal,
    val problem: String? = null
) {
    val key: String get() = "$kind:$id"
}

@Serializable
data class CurrencyGroupDto(val key: String, val title: String, val hint: String? = null, val items: List<CurrencyItemDto> = emptyList())

@Serializable
data class CurrencyPreviewDto(
    val currentCurrency: String,
    val newCurrency: String,
    val canChange: Boolean = false,
    val blockers: List<String> = emptyList(),
    val groups: List<CurrencyGroupDto> = emptyList(),
    val valueCount: Int = 0,
    val recipesFollowing: Long = 0,
    val previewToken: String = ""
)

@Serializable
data class CurrencyChangeRecordDto(
    val id: String,
    val oldCurrency: String,
    val newCurrency: String,
    @Serializable(with = OrderDecimalSerializer::class) val rate: OrderDecimal,
    @Serializable(with = OrderDecimalSerializer::class) val roundingStep: OrderDecimal,
    val valuesConverted: Int = 0,
    val changedAt: String? = null,
    val changedByName: String? = null
)

// ---- Time zone change ----

@Serializable
data class TimezonePreviewRequestDto(val timezone: String)

@Serializable
data class BookingExampleDto(
    val code: String? = null,
    val name: String? = null,
    val before: String = "",
    val afterKeepingMoment: String = "",
    val afterKeepingClock: String = ""
)

@Serializable
data class TimezonePreviewDto(
    val currentTimezone: String,
    val newTimezone: String,
    val differenceMinutes: Int = 0,
    val upcomingBookings: Long = 0,
    val openOrders: Long = 0,
    val openShifts: Long = 0,
    val needsConfirmation: Boolean = false,
    val examples: List<BookingExampleDto> = emptyList(),
    val notes: List<String> = emptyList(),
    val previewToken: String = ""
)

@Serializable
data class TimezoneChangeRequestDto(val timezone: String, val keepBookingTimes: Boolean, val confirmed: Boolean, val previewToken: String)

// ---- Co-Super-Admins ----

@Serializable
data class CoSuperAdminPermissionDto(val code: String, val name: String, val description: String? = null, val granted: Boolean = true)

@Serializable
data class GrantedPermissionsRequestDto(val grantedCodes: List<String>)

interface PlatformRepository {
    suspend fun cards(search: String, filter: String?, page: Int, size: Int): PageDto<RestaurantCardDto>
    suspend fun createRestaurant(request: CreateRestaurantRequestDto): RestaurantDto
    suspend fun restaurant(restaurantId: String): RestaurantDto
    suspend fun updateRestaurant(restaurantId: String, request: UpdateRestaurantRequestDto): RestaurantDto
    suspend fun setRestaurantStatus(restaurantId: String, request: StatusRequestDto): RestaurantDto
    suspend fun removeRestaurant(restaurantId: String)
    /** The order of the restaurant cards (listed ones first). */
    suspend fun setRestaurantOrder(restaurantIds: List<String>)
    suspend fun resendOwnerInvite(restaurantId: String)
    suspend fun ownerInvite(restaurantId: String): OwnerInviteStatusDto
    /** The second step of setting up a restaurant: its owner, who gets an email to set a password. */
    suspend fun createOwner(restaurantId: String, owner: OwnerInviteDto): RestaurantDto
    /** Corrects the owner's name, phone, or (while the invitation waits) email. */
    suspend fun updateOwner(restaurantId: String, owner: UpdateOwnerRequestDto): RestaurantDto

    suspend fun branches(restaurantId: String): List<BranchDto>
    suspend fun createBranch(restaurantId: String, request: CreateBranchRequestDto): BranchDto
    suspend fun updateBranch(restaurantId: String, branchId: String, request: UpdateBranchRequestDto): BranchDto
    suspend fun setBranchStatus(restaurantId: String, branchId: String, request: StatusRequestDto): BranchDto
    suspend fun branchRemovalCheck(restaurantId: String, branchId: String): BranchRemovalCheckDto
    suspend fun removeBranch(restaurantId: String, branchId: String, request: BranchRemovalRequestDto)

    suspend fun addresses(restaurantId: String, branchId: String? = null): List<AddressDto>
    suspend fun saveAddress(restaurantId: String, branchId: String?, addressId: String?, request: AddressRequestDto): AddressDto
    suspend fun removeAddress(restaurantId: String, branchId: String?, addressId: String)

    suspend fun contacts(restaurantId: String): List<ContactDto>
    suspend fun saveContact(restaurantId: String, contactId: String?, request: ContactRequestDto): ContactDto
    suspend fun removeContact(restaurantId: String, contactId: String)

    /** Null when the restaurant has no branding yet. */
    suspend fun branding(restaurantId: String): BrandingDto?
    suspend fun saveBranding(restaurantId: String, request: BrandingRequestDto): BrandingDto
    suspend fun uploadLogo(restaurantId: String, bytes: ByteArray, fileName: String, contentType: String): BrandingDto
    suspend fun removeLogo(restaurantId: String): BrandingDto

    suspend fun taxProfiles(restaurantId: String): List<TaxProfileDto>
    suspend fun saveTaxProfile(restaurantId: String, taxProfileId: String?, request: TaxProfileRequestDto): TaxProfileDto
    suspend fun removeTaxProfile(restaurantId: String, taxProfileId: String)

    suspend fun currencyPreview(restaurantId: String, request: CurrencyPreviewRequestDto): CurrencyPreviewDto
    suspend fun changeCurrency(restaurantId: String, request: CurrencyChangeRequestDto): CurrencyPreviewDto
    suspend fun currencyChanges(restaurantId: String): List<CurrencyChangeRecordDto>

    suspend fun timezonePreview(restaurantId: String, timezone: String): TimezonePreviewDto
    suspend fun changeTimezone(restaurantId: String, request: TimezoneChangeRequestDto): RestaurantDto

    /** One page of the "Choose a branch" cards, open ones first; [search] matches the name, code, city or street. */
    suspend fun branchCards(restaurantId: String, search: String = "", page: Int = 0, size: Int = 24): BranchCardPageDto
    suspend fun branch(restaurantId: String, branchId: String): BranchDto
    /** [branchId] null: the restaurant's own cover. */
    suspend fun uploadCover(restaurantId: String, branchId: String?, bytes: ByteArray, fileName: String, contentType: String): CoverDto
    suspend fun removeCover(restaurantId: String, branchId: String?): CoverDto

    suspend fun coSuperAdminPermissions(userId: String): List<CoSuperAdminPermissionDto>
    suspend fun setCoSuperAdminPermissions(userId: String, grantedCodes: List<String>): List<CoSuperAdminPermissionDto>

    /** Active staff of the restaurant who could become its owner. */
    suspend fun ownerCandidates(restaurantId: String, search: String): List<OwnerCandidateDto>
    /** The Super Admin gives the restaurant to a new owner; the former owner loses access. */
    suspend fun transferOwnership(restaurantId: String, request: OwnerTransferRequestDto): OwnerTransferDto
    suspend fun ownerTransfers(restaurantId: String): List<OwnerTransferDto>

    /** Restaurant applications: [status] PENDING (oldest first), REJECTED or ALL. */
    suspend fun applications(status: String, search: String, page: Int, size: Int): PageDto<RestaurantApplicationDto>
    suspend fun reviewApplication(applicationId: String, request: ReviewApplicationRequestDto): RestaurantApplicationDto
}

class PlatformApi(
    private val client: HttpClient,
    private val baseUrlProvider: () -> String = { ApiConfig.BASE_URL }
) : PlatformRepository {

    private fun url(path: String) = "${baseUrlProvider().trimEnd('/')}$path"
    private fun id(value: String) = value.encodeURLPathPart()
    private fun restaurantUrl(restaurantId: String) = url("/restaurants/${id(restaurantId)}")
    private fun place(restaurantId: String, branchId: String?) =
        if (branchId == null) restaurantUrl(restaurantId) else "${restaurantUrl(restaurantId)}/branches/${id(branchId)}"

    override suspend fun cards(search: String, filter: String?, page: Int, size: Int): PageDto<RestaurantCardDto> =
        client.get(url("/restaurants/cards")) {
            search.trim().takeIf { it.isNotEmpty() }?.let { parameter("search", it) }
            filter?.let { parameter("filter", it) }
            parameter("page", page)
            parameter("size", size)
        }.body()

    override suspend fun createRestaurant(request: CreateRestaurantRequestDto): RestaurantDto =
        client.post(url("/restaurants")) { contentType(ContentType.Application.Json); setBody(request) }.body()

    override suspend fun restaurant(restaurantId: String): RestaurantDto = client.get(restaurantUrl(restaurantId)).body()

    override suspend fun updateRestaurant(restaurantId: String, request: UpdateRestaurantRequestDto): RestaurantDto =
        client.put(restaurantUrl(restaurantId)) { contentType(ContentType.Application.Json); setBody(request) }.body()

    override suspend fun setRestaurantStatus(restaurantId: String, request: StatusRequestDto): RestaurantDto =
        client.patch("${restaurantUrl(restaurantId)}/status") { contentType(ContentType.Application.Json); setBody(request) }.body()

    override suspend fun removeRestaurant(restaurantId: String) {
        client.delete(restaurantUrl(restaurantId))
    }

    override suspend fun setRestaurantOrder(restaurantIds: List<String>) {
        client.put(url("/restaurants/order")) { contentType(ContentType.Application.Json); setBody(RestaurantOrderRequestDto(restaurantIds)) }
    }

    override suspend fun createOwner(restaurantId: String, owner: OwnerInviteDto): RestaurantDto =
        client.post("${restaurantUrl(restaurantId)}/owner") { contentType(ContentType.Application.Json); setBody(owner) }.body()

    override suspend fun updateOwner(restaurantId: String, owner: UpdateOwnerRequestDto): RestaurantDto =
        client.put("${restaurantUrl(restaurantId)}/owner") { contentType(ContentType.Application.Json); setBody(owner) }.body()

    override suspend fun resendOwnerInvite(restaurantId: String) {
        client.post("${restaurantUrl(restaurantId)}/owner/invite")
    }

    override suspend fun ownerInvite(restaurantId: String): OwnerInviteStatusDto =
        client.get("${restaurantUrl(restaurantId)}/owner/invite").body()

    override suspend fun branches(restaurantId: String): List<BranchDto> {
        val branches = mutableListOf<BranchDto>()
        var page = 0
        do {
            val result = client.get("${restaurantUrl(restaurantId)}/branches") {
                parameter("page", page++)
                parameter("size", 100)
                parameter("sortBy", "name")
                parameter("direction", "asc")
            }.body<PageDto<BranchDto>>()
            branches += result.items
        } while (result.hasNext)
        return branches.distinctBy { it.id }
    }

    override suspend fun createBranch(restaurantId: String, request: CreateBranchRequestDto): BranchDto =
        client.post("${restaurantUrl(restaurantId)}/branches") { contentType(ContentType.Application.Json); setBody(request) }.body()

    override suspend fun updateBranch(restaurantId: String, branchId: String, request: UpdateBranchRequestDto): BranchDto =
        client.put("${restaurantUrl(restaurantId)}/branches/${id(branchId)}") { contentType(ContentType.Application.Json); setBody(request) }.body()

    override suspend fun setBranchStatus(restaurantId: String, branchId: String, request: StatusRequestDto): BranchDto =
        client.patch("${restaurantUrl(restaurantId)}/branches/${id(branchId)}/status") { contentType(ContentType.Application.Json); setBody(request) }.body()

    override suspend fun branchRemovalCheck(restaurantId: String, branchId: String): BranchRemovalCheckDto =
        client.get("${restaurantUrl(restaurantId)}/branches/${id(branchId)}/removal-check").body()

    override suspend fun removeBranch(restaurantId: String, branchId: String, request: BranchRemovalRequestDto) {
        client.delete("${restaurantUrl(restaurantId)}/branches/${id(branchId)}") {
            contentType(ContentType.Application.Json); setBody(request)
        }
    }

    override suspend fun addresses(restaurantId: String, branchId: String?): List<AddressDto> =
        client.get("${place(restaurantId, branchId)}/addresses").body()

    override suspend fun saveAddress(restaurantId: String, branchId: String?, addressId: String?, request: AddressRequestDto): AddressDto =
        if (addressId == null) client.post("${place(restaurantId, branchId)}/addresses") { contentType(ContentType.Application.Json); setBody(request) }.body()
        else client.put("${place(restaurantId, branchId)}/addresses/${id(addressId)}") { contentType(ContentType.Application.Json); setBody(request) }.body()

    override suspend fun removeAddress(restaurantId: String, branchId: String?, addressId: String) {
        client.delete("${place(restaurantId, branchId)}/addresses/${id(addressId)}")
    }

    override suspend fun contacts(restaurantId: String): List<ContactDto> = client.get("${restaurantUrl(restaurantId)}/contacts").body()

    override suspend fun saveContact(restaurantId: String, contactId: String?, request: ContactRequestDto): ContactDto =
        if (contactId == null) client.post("${restaurantUrl(restaurantId)}/contacts") { contentType(ContentType.Application.Json); setBody(request) }.body()
        else client.put("${restaurantUrl(restaurantId)}/contacts/${id(contactId)}") { contentType(ContentType.Application.Json); setBody(request) }.body()

    override suspend fun removeContact(restaurantId: String, contactId: String) {
        client.delete("${restaurantUrl(restaurantId)}/contacts/${id(contactId)}")
    }

    override suspend fun branding(restaurantId: String): BrandingDto? = try {
        client.get("${restaurantUrl(restaurantId)}/branding").body()
    } catch (e: com.saporini.mobile_desktop.core.network.ApiException) {
        if (e.status == 404) null else throw e
    }

    override suspend fun saveBranding(restaurantId: String, request: BrandingRequestDto): BrandingDto =
        client.put("${restaurantUrl(restaurantId)}/branding") { contentType(ContentType.Application.Json); setBody(request) }.body()

    override suspend fun uploadLogo(restaurantId: String, bytes: ByteArray, fileName: String, contentType: String): BrandingDto =
        client.put("${restaurantUrl(restaurantId)}/branding/logo") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            key = "file",
                            value = bytes,
                            headers = Headers.build {
                                append(HttpHeaders.ContentType, contentType)
                                append(HttpHeaders.ContentDisposition, "filename=\"${fileName.replace("\"", "")}\"")
                            }
                        )
                    }
                )
            )
        }.body()

    override suspend fun removeLogo(restaurantId: String): BrandingDto = client.delete("${restaurantUrl(restaurantId)}/branding/logo").body()

    override suspend fun taxProfiles(restaurantId: String): List<TaxProfileDto> = client.get("${restaurantUrl(restaurantId)}/tax-profiles").body()

    override suspend fun saveTaxProfile(restaurantId: String, taxProfileId: String?, request: TaxProfileRequestDto): TaxProfileDto =
        if (taxProfileId == null) client.post("${restaurantUrl(restaurantId)}/tax-profiles") { contentType(ContentType.Application.Json); setBody(request) }.body()
        else client.put("${restaurantUrl(restaurantId)}/tax-profiles/${id(taxProfileId)}") { contentType(ContentType.Application.Json); setBody(request) }.body()

    override suspend fun removeTaxProfile(restaurantId: String, taxProfileId: String) {
        client.delete("${restaurantUrl(restaurantId)}/tax-profiles/${id(taxProfileId)}")
    }

    override suspend fun currencyPreview(restaurantId: String, request: CurrencyPreviewRequestDto): CurrencyPreviewDto =
        client.post("${restaurantUrl(restaurantId)}/currency/preview") { contentType(ContentType.Application.Json); setBody(request) }.body()

    override suspend fun changeCurrency(restaurantId: String, request: CurrencyChangeRequestDto): CurrencyPreviewDto =
        client.post("${restaurantUrl(restaurantId)}/currency/change") { contentType(ContentType.Application.Json); setBody(request) }.body()

    override suspend fun currencyChanges(restaurantId: String): List<CurrencyChangeRecordDto> =
        client.get("${restaurantUrl(restaurantId)}/currency/changes").body()

    override suspend fun timezonePreview(restaurantId: String, timezone: String): TimezonePreviewDto =
        client.post("${restaurantUrl(restaurantId)}/timezone/preview") { contentType(ContentType.Application.Json); setBody(TimezonePreviewRequestDto(timezone)) }.body()

    override suspend fun changeTimezone(restaurantId: String, request: TimezoneChangeRequestDto): RestaurantDto =
        client.post("${restaurantUrl(restaurantId)}/timezone/change") { contentType(ContentType.Application.Json); setBody(request) }.body()

    override suspend fun branchCards(restaurantId: String, search: String, page: Int, size: Int): BranchCardPageDto =
        client.get("${restaurantUrl(restaurantId)}/branches/cards") {
            search.trim().takeIf { it.isNotEmpty() }?.let { parameter("search", it) }
            parameter("page", page)
            parameter("size", size)
        }.body()

    override suspend fun branch(restaurantId: String, branchId: String): BranchDto =
        client.get("${restaurantUrl(restaurantId)}/branches/${id(branchId)}").body()

    override suspend fun uploadCover(restaurantId: String, branchId: String?, bytes: ByteArray, fileName: String, contentType: String): CoverDto =
        client.put("${place(restaurantId, branchId)}/cover") { setBody(picture(bytes, fileName, contentType)) }.body()

    override suspend fun removeCover(restaurantId: String, branchId: String?): CoverDto =
        client.delete("${place(restaurantId, branchId)}/cover").body()

    private fun picture(bytes: ByteArray, fileName: String, contentType: String) = MultiPartFormDataContent(
        formData {
            append(
                key = "file",
                value = bytes,
                headers = Headers.build {
                    append(HttpHeaders.ContentType, contentType)
                    append(HttpHeaders.ContentDisposition, "filename=\"${fileName.replace("\"", "")}\"")
                }
            )
        }
    )

    override suspend fun coSuperAdminPermissions(userId: String): List<CoSuperAdminPermissionDto> =
        client.get(url("/co-super-admins/${id(userId)}/permissions")).body()

    override suspend fun setCoSuperAdminPermissions(userId: String, grantedCodes: List<String>): List<CoSuperAdminPermissionDto> =
        client.put(url("/co-super-admins/${id(userId)}/permissions")) {
            contentType(ContentType.Application.Json); setBody(GrantedPermissionsRequestDto(grantedCodes))
        }.body()

    override suspend fun ownerCandidates(restaurantId: String, search: String): List<OwnerCandidateDto> =
        client.get("${restaurantUrl(restaurantId)}/owner/candidates") {
            search.trim().takeIf { it.isNotEmpty() }?.let { parameter("search", it) }
        }.body()

    override suspend fun transferOwnership(restaurantId: String, request: OwnerTransferRequestDto): OwnerTransferDto =
        client.post("${restaurantUrl(restaurantId)}/owner/transfer") { contentType(ContentType.Application.Json); setBody(request) }.body()

    override suspend fun ownerTransfers(restaurantId: String): List<OwnerTransferDto> =
        client.get("${restaurantUrl(restaurantId)}/owner/transfers").body()

    override suspend fun applications(status: String, search: String, page: Int, size: Int): PageDto<RestaurantApplicationDto> =
        client.get(url("/platform/restaurant-applications")) {
            parameter("status", status)
            search.trim().takeIf { it.isNotEmpty() }?.let { parameter("search", it) }
            parameter("page", page)
            parameter("size", size)
        }.body()

    override suspend fun reviewApplication(applicationId: String, request: ReviewApplicationRequestDto): RestaurantApplicationDto =
        client.patch(url("/restaurants/registrations/${id(applicationId)}/review")) {
            contentType(ContentType.Application.Json); setBody(request)
        }.body()
}
