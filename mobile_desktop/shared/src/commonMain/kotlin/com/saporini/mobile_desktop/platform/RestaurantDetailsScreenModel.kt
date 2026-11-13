package com.saporini.mobile_desktop.platform

import cafe.adriel.voyager.core.model.ScreenModel
import com.saporini.mobile_desktop.admin.people.PeopleRepository
import com.saporini.mobile_desktop.admin.people.StaffUserDto
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.session.isPlatformAdmin
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class RestaurantTab(val label: String) {
    DETAILS("Details"),
    BRANCHES("Branches"),
    PLACES("Address & contacts"),
    BRANDING("Logo & receipts"),
    LEGAL("Legal & tax"),
    MONEY("Currency & time zone")
}

data class DetailsDraft(
    val name: String,
    val legalName: String,
    val code: String,
    val description: String,
    val email: String,
    val phone: String,
    val website: String,
    val showProblems: Boolean = false,
    val expectedVersion: Long = 0
)

fun detailsProblems(draft: DetailsDraft): Map<String, String> = buildMap {
    lengthProblem(draft.name, MAX_RESTAURANT_NAME, true, "the name")?.let { put("name", it) }
    lengthProblem(draft.legalName, MAX_LEGAL_NAME, true, "the legal name")?.let { put("legalName", it) }
    lengthProblem(draft.code, MAX_CODE, true, "a code")?.let { put("code", it) }
    lengthProblem(draft.description, MAX_DESCRIPTION)?.let { put("description", it) }
    // Guests see both on the booking pages and in their emails.
    emailProblem(draft.email, required = true)?.let { put("email", it) }
    (if (draft.phone.isBlank()) "Enter a phone number" else phoneProblem(draft.phone))?.let { put("phone", it) }
    websiteProblem(draft.website)?.let { put("website", it) }
}

/** A branch being added ([id] null) or changed, with the address guests find it at. */
data class BranchDraft(
    val id: String? = null,
    val name: String = "",
    val code: String = "",
    val description: String = "",
    val email: String = "",
    val phone: String = "",
    val street: String = "",
    val city: String = "",
    val postalCode: String = "",
    val country: String = "",
    val addressId: String? = null,
    val showProblems: Boolean = false,
    val expectedVersion: Long = 0
)

fun branchProblems(draft: BranchDraft): Map<String, String> = buildMap {
    lengthProblem(draft.name, MAX_BRANCH_NAME, true, "a name")?.let { put("name", it) }
    lengthProblem(draft.code, MAX_CODE)?.let { put("code", it) }
    lengthProblem(draft.description, MAX_DESCRIPTION)?.let { put("description", it) }
    emailProblem(draft.email)?.let { put("email", it) }
    phoneProblem(draft.phone)?.let { put("phone", it) }
    // An address is optional, but once started it needs a street, city and country.
    val started = listOf(draft.street, draft.city, draft.postalCode, draft.country).any { it.isNotBlank() }
    if (started) {
        lengthProblem(draft.street, MAX_STREET, true, "the street")?.let { put("street", it) }
        lengthProblem(draft.city, MAX_CITY, true, "the city")?.let { put("city", it) }
        lengthProblem(draft.country, MAX_CITY, true, "the country")?.let { put("country", it) }
        lengthProblem(draft.postalCode, MAX_POSTAL)?.let { put("postalCode", it) }
    }
}

data class AddressDraft(
    val id: String? = null,
    val type: String = "PHYSICAL",
    val street: String = "",
    val street2: String = "",
    val city: String = "",
    val postalCode: String = "",
    val country: String = "",
    val primary: Boolean = true,
    val showProblems: Boolean = false
)

fun addressProblems(draft: AddressDraft): Map<String, String> = buildMap {
    lengthProblem(draft.street, MAX_STREET, true, "the street")?.let { put("street", it) }
    lengthProblem(draft.street2, MAX_STREET)?.let { put("street2", it) }
    lengthProblem(draft.city, MAX_CITY, true, "the city")?.let { put("city", it) }
    lengthProblem(draft.postalCode, MAX_POSTAL)?.let { put("postalCode", it) }
    lengthProblem(draft.country, MAX_CITY, true, "the country")?.let { put("country", it) }
}

data class ContactDraft(
    val id: String? = null,
    val type: String = "GENERAL",
    val fullName: String = "",
    val jobTitle: String = "",
    val email: String = "",
    val phone: String = "",
    val primary: Boolean = false,
    val showProblems: Boolean = false
)

fun contactProblems(draft: ContactDraft): Map<String, String> = buildMap {
    lengthProblem(draft.fullName, 150, true, "a name")?.let { put("fullName", it) }
    lengthProblem(draft.jobTitle, 100)?.let { put("jobTitle", it) }
    emailProblem(draft.email)?.let { put("email", it) }
    if (draft.phone.trim().length > 50) put("phone", "At most 50 characters")
    if (draft.email.isBlank() && draft.phone.isBlank()) put("email", "Enter an email or a phone")
}

data class TaxDraft(
    val id: String? = null,
    val country: String = "",
    val vatNumber: String = "",
    val taxNumber: String = "",
    val fiscalCode: String = "",
    val taxOffice: String = "",
    val isDefault: Boolean = true,
    val showProblems: Boolean = false,
    val effectiveFrom: String = "",
    val effectiveTo: String = ""
)

fun taxProblems(draft: TaxDraft): Map<String, String> = buildMap {
    lengthProblem(draft.country, 100, true, "the country")?.let { put("country", it) }
    listOf("vatNumber" to draft.vatNumber, "taxNumber" to draft.taxNumber, "fiscalCode" to draft.fiscalCode).forEach { (key, value) ->
        lengthProblem(value, MAX_TAX_FIELD)?.let { put(key, it) }
    }
    lengthProblem(draft.taxOffice, 150)?.let { put("taxOffice", it) }
    val from = taxInstant(draft.effectiveFrom)
    val to = taxInstant(draft.effectiveTo)
    if (draft.effectiveFrom.isNotBlank() && from == null) put("effectiveFrom", "Enter a date and time with an offset, e.g. 2026-10-13T00:00:00+02:00")
    if (draft.effectiveTo.isNotBlank() && to == null) put("effectiveTo", "Enter a date and time with an offset, e.g. 2026-10-14T00:00:00+02:00")
    if (from != null && to != null && to <= from) put("effectiveTo", "The end must be after the start")
    if (draft.vatNumber.isBlank() && draft.taxNumber.isBlank() && draft.fiscalCode.isBlank()) put("vatNumber", "Enter at least the VAT or tax number")
}

data class BrandingDraft(
    val primaryColor: String = "",
    val secondaryColor: String = "",
    val receiptHeader: String = "",
    val receiptFooter: String = ""
)

/** The guided currency change: choose → preview (correct any value) → confirm. */
data class CurrencyFlow(
    val newCurrency: String = "",
    val rateText: String = "",
    val step: String = "0.01",
    val preview: CurrencyPreviewDto? = null,
    // Values corrected by hand in the preview, by item key ("DISH_PRICE:id").
    val corrections: Map<String, String> = emptyMap(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null
) {
    val rate: OrderDecimal? get() = rateText.trim().replace(',', '.').takeIf { RATE.matches(it) && it.toDoubleOrNull()?.let { v -> v > 0 } == true }?.let(::OrderDecimal)

    companion object {
        private val RATE = Regex("^(0|[1-9][0-9]{0,6})(\\.[0-9]{1,8})?$")
    }
}

/** The time zone change: choose → see what moves → confirm. */
data class TimezoneFlow(
    val zone: String = "",
    val preview: TimezonePreviewDto? = null,
    val keepBookingTimes: Boolean = true,
    val confirmed: Boolean = false,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null
)

/** Who gets the restaurant: someone already in the POS, or a new person who is invited by email. */
enum class TransferMode { EXISTING, INVITE }

/**
 * The Super Admin giving the restaurant to a new owner. The former owner loses access when it's saved, so it asks for
 * the restaurant's name typed out, like removing a branch.
 */
data class OwnerTransferStep(
    val restaurantId: String,
    val restaurantName: String,
    val expectedVersion: Long,
    val currentOwnerName: String,
    val mode: TransferMode = TransferMode.EXISTING,
    val search: String = "",
    val candidates: List<OwnerCandidateDto> = emptyList(),
    val loadingCandidates: Boolean = false,
    val candidatesError: String? = null,
    val chosen: OwnerCandidateDto? = null,
    val invite: OwnerDraft = OwnerDraft(),
    val reason: String = "",
    val confirmedName: String = "",
    val showProblems: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null
) {
    val newOwnerName: String
        get() = if (mode == TransferMode.EXISTING) chosen?.displayName.orEmpty()
            else "${invite.firstName.trim()} ${invite.lastName.trim()}".trim()
}

const val MAX_TRANSFER_REASON = 500

fun transferProblems(step: OwnerTransferStep): Map<String, String> = buildMap {
    if (step.mode == TransferMode.EXISTING && step.chosen == null) put("person", "Choose who gets the restaurant")
    if (step.mode == TransferMode.INVITE) ownerProblems(step.invite).forEach { (key, value) -> put("invite.$key", value) }
    lengthProblem(step.reason, MAX_TRANSFER_REASON)?.let { put("reason", it) }
    if (step.confirmedName.trim() != step.restaurantName.trim()) put("confirm", "Type the restaurant's name exactly as shown")
}

sealed interface RestaurantConfirm {
    data class Status(val open: Boolean, val status: String) : RestaurantConfirm
    data object RemoveRestaurant : RestaurantConfirm
    data class RemoveBranch(val branch: BranchDto, val check: BranchRemovalCheckDto?, val staffAction: String? = null, val confirmedName: String = "") : RestaurantConfirm
    data class BranchStatus(val branch: BranchDto, val status: String) : RestaurantConfirm
    data class RemoveAddress(val address: AddressDto) : RestaurantConfirm
    data class RemoveContact(val contact: ContactDto) : RestaurantConfirm
    data class RemoveTax(val tax: TaxProfileDto) : RestaurantConfirm
}

data class RestaurantDetailsState(
    val restaurantId: String? = null,
    val superAdmin: Boolean = false,
    val canRead: Boolean = false,
    val canEdit: Boolean = false,
    val canManage: Boolean = false,
    val canDelete: Boolean = false,
    val tab: RestaurantTab = RestaurantTab.DETAILS,
    val restaurant: RestaurantDto? = null,
    val owner: StaffUserDto? = null,
    // How the owner's invitation is doing (only while they haven't set a password).
    val invite: OwnerInviteStatusDto? = null,
    val branches: List<BranchDto> = emptyList(),
    val branchAddresses: Map<String, List<AddressDto>> = emptyMap(),
    val addresses: List<AddressDto> = emptyList(),
    val contacts: List<ContactDto> = emptyList(),
    val branding: BrandingDto? = null,
    val brandingDraft: BrandingDraft = BrandingDraft(),
    val taxProfiles: List<TaxProfileDto> = emptyList(),
    val currencyChanges: List<CurrencyChangeRecordDto> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val uploadingLogo: Boolean = false,
    val details: DetailsDraft? = null,
    val branch: BranchDraft? = null,
    val address: AddressDraft? = null,
    val contact: ContactDraft? = null,
    val tax: TaxDraft? = null,
    val currency: CurrencyFlow? = null,
    val timezone: TimezoneFlow? = null,
    val confirm: RestaurantConfirm? = null,
    // Adding the owner of a restaurant that has none yet.
    val ownerStep: OwnerStep? = null,
    // Only the Super Admin gives a restaurant to a new owner.
    val canTransferOwner: Boolean = false,
    val transfer: OwnerTransferStep? = null,
    // Every change of the restaurant's owner, newest first (super admins).
    val ownerTransfers: List<OwnerTransferDto> = emptyList(),
    // The restaurant was removed: the screen goes back to the restaurant list.
    val removed: Boolean = false,
    val notice: String? = null,
    val error: String? = null
) {
    val brandingChanged: Boolean
        get() = brandingDraft != brandingDraftOf(branding)
}

fun brandingDraftOf(branding: BrandingDto?) = BrandingDraft(
    branding?.primaryColor.orEmpty(), branding?.secondaryColor.orEmpty(), branding?.receiptHeader.orEmpty(), branding?.receiptFooter.orEmpty()
)

/**
 * One restaurant's own details: what it's called and how guests reach it, its branches, addresses and contacts, logo
 * and receipt texts, legal and tax details, and its currency and time zone. Super admins change everything (the
 * Restaurant app); the owner changes what guests see (Admin Hub → Restaurant info) and the server refuses the rest.
 */
class RestaurantDetailsScreenModel(
    private val repository: PlatformRepository,
    private val people: PeopleRepository,
    private val session: SessionManager
) : ScreenModel {

    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var actions = CoroutineScope(work.coroutineContext + SupervisorJob(work.coroutineContext[Job]))
    private val mutable = MutableStateFlow(RestaurantDetailsState())
    val state: StateFlow<RestaurantDetailsState> = mutable.asStateFlow()
    private val writes = Mutex()
    private var revision = 0L
    private var signIn = 0L
    private var active = false
    private var loadJob: Job? = null

    init {
        work.watchRestaurantPermissions(session) { active }
        work.launch {
            session.currentUser.collectLatest { user ->
                actions.cancel()
                actions = CoroutineScope(work.coroutineContext + SupervisorJob(work.coroutineContext[Job]))
                revision++
                signIn++
                loadJob?.cancel()
                val access = restaurantAccess(user)
                val platform = isPlatformAdmin(user)
                mutable.value = RestaurantDetailsState(
                    restaurantId = user?.restaurantId,
                    superAdmin = platform,
                    canRead = access.read,
                    canEdit = access.edit,
                    canManage = access.manage,
                    canDelete = access.delete,
                    canTransferOwner = com.saporini.mobile_desktop.core.session.isSuperAdmin(user) && access.manage
                )
                if (active) load()
            }
        }
    }

    fun setActive(value: Boolean) {
        if (value == active) return
        active = value
        if (value) load() else {
            revision++
            loadJob?.cancel()
            mutable.update { it.copy(loading = false) }
        }
    }

    fun refresh() = load()

    fun tab(tab: RestaurantTab) = mutable.update { it.copy(tab = tab) }

    private fun load(preserveError: Boolean = false) {
        val restaurantId = state.value.restaurantId ?: return
        if (!active || !state.value.canRead) return
        val token = ++revision
        val platform = state.value.superAdmin
        loadJob?.cancel()
        loadJob = actions.launch {
            mutable.update { it.copy(loading = true) }
            try {
                session.requireRestaurantAccess { it.read }
                coroutineScope {
                    val restaurant = async { repository.restaurant(restaurantId) }
                    val branches = async { repository.branches(restaurantId) }
                    val addresses = async { repository.addresses(restaurantId) }
                    val contacts = async { repository.contacts(restaurantId) }
                    val branding = async { repository.branding(restaurantId) }
                    val taxes = async { repository.taxProfiles(restaurantId) }
                    val changes = async { runCatching { repository.currencyChanges(restaurantId) }.getOrDefault(emptyList()) }
                    val transfers = async { if (platform) runCatching { repository.ownerTransfers(restaurantId) }.getOrDefault(emptyList()) else emptyList() }
                    val loadedBranches = branches.await()
                    val branchAddresses = loadedBranches.map { branch ->
                        async { branch.id to runCatching { repository.addresses(restaurantId, branch.id) }.getOrDefault(emptyList()) }
                    }.awaitAll().toMap()
                    val loadedRestaurant = restaurant.await()
                    // Only super admins look up the owner (the owner knows who they are).
                    val owner = if (platform) loadedRestaurant.ownerUserId?.let { id -> runCatching { people.person(id) }.getOrNull() } else null
                    val invite = if (owner != null && !owner.emailVerified) runCatching { repository.ownerInvite(restaurantId) }.getOrNull() else null
                    val loadedBranding = branding.await()
                    if (token != revision) return@coroutineScope
                    session.renameActingRestaurant(loadedRestaurant.id, loadedRestaurant.name)
                    mutable.update { state ->
                        state.copy(
                            restaurant = loadedRestaurant, owner = owner, invite = invite, branches = loadedBranches.sortedWith(compareByDescending<BranchDto> { it.open }.thenBy { it.name.lowercase() }),
                            branchAddresses = branchAddresses, addresses = addresses.await(), contacts = contacts.await(),
                            branding = loadedBranding,
                            // Keep what is being typed; a refresh only replaces untouched texts.
                            brandingDraft = if (state.brandingChanged && state.loaded) state.brandingDraft else brandingDraftOf(loadedBranding),
                            taxProfiles = taxes.await(), currencyChanges = changes.await(), ownerTransfers = transfers.await(),
                            loaded = true, error = if (preserveError) state.error else null
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == revision) mutable.update { it.copy(error = platformMessage(e, write = false)) }
            } finally {
                if (token == revision) mutable.update { it.copy(loading = false) }
            }
        }
    }

    // ---- Details ----

    fun editDetails() {
        val restaurant = state.value.restaurant ?: return
        if (!state.value.canEdit) return
        mutable.update {
            it.copy(details = DetailsDraft(restaurant.name, restaurant.legalName, restaurant.code, restaurant.description.orEmpty(),
                restaurant.email.orEmpty(), restaurant.phone.orEmpty(), restaurant.website.orEmpty(), expectedVersion = restaurant.version), notice = null)
        }
    }

    /** The Details tab's form: the saved details, ready to change in place (unlike [editDetails], a notice stays up). */
    fun openDetailsForm() {
        val restaurant = state.value.restaurant ?: return
        if (!state.value.canEdit) return
        mutable.update {
            it.copy(details = DetailsDraft(restaurant.name, restaurant.legalName, restaurant.code, restaurant.description.orEmpty(),
                restaurant.email.orEmpty(), restaurant.phone.orEmpty(), restaurant.website.orEmpty(), expectedVersion = restaurant.version))
        }
    }

    fun changeDetails(change: (DetailsDraft) -> DetailsDraft) = mutable.update { state -> state.details?.let { state.copy(details = change(it)) } ?: state }

    fun cancelDetails() = mutable.update { it.copy(details = null) }

    fun saveDetails() {
        if (!state.value.canEdit) return
        val current = state.value
        val draft = current.details ?: return
        val restaurant = current.restaurant ?: return
        if (detailsProblems(draft).isNotEmpty()) {
            mutable.update { it.copy(details = draft.copy(showProblems = true)) }
            return
        }
        // The owner can't change who the restaurant is; their form leaves those as they are.
        val request = UpdateRestaurantRequestDto(
            name = if (current.superAdmin) draft.name.trim() else restaurant.name,
            legalName = if (current.superAdmin) draft.legalName.trim() else restaurant.legalName,
            code = if (current.superAdmin) draft.code.trim() else restaurant.code,
            slug = restaurant.slug,
            description = draft.description.trim().ifBlank { null },
            email = draft.email.trim().ifBlank { null },
            phone = draft.phone.trim().ifBlank { null },
            website = draft.website.trim().ifBlank { null },
            currency = restaurant.currency,
            timezone = restaurant.timezone,
            ownerUserId = restaurant.ownerUserId,
            isActive = restaurant.isActive,
            status = restaurant.status,
            expectedVersion = draft.expectedVersion
        )
        write("The details were saved") { restaurantId ->
            val saved = repository.updateRestaurant(restaurantId, request)
            session.renameActingRestaurant(saved.id, saved.name)
            mutable.update { it.copy(restaurant = saved, details = null) }
        }
    }

    // ---- Opening, closing and removing ----

    fun askStatus(open: Boolean, status: String) = if (state.value.canManage) mutable.update { it.copy(confirm = RestaurantConfirm.Status(open, status)) } else Unit

    fun askRemoveRestaurant() = if (state.value.canDelete) mutable.update { it.copy(confirm = RestaurantConfirm.RemoveRestaurant) } else Unit

    fun dismissConfirm() = mutable.update { it.copy(confirm = null) }

    fun confirm() {
        when (val confirm = state.value.confirm ?: return) {
            is RestaurantConfirm.Status -> write(if (confirm.open) "The restaurant is open again" else "The restaurant is ${restaurantStatusLabel(confirm.status, false).lowercase()}. Its staff are signed out.", { it.manage }) { restaurantId ->
                val saved = repository.setRestaurantStatus(restaurantId, StatusRequestDto(confirm.open, confirm.status))
                mutable.update { it.copy(restaurant = saved, confirm = null) }
            }
            RestaurantConfirm.RemoveRestaurant -> write(null, { it.delete }) { restaurantId ->
                repository.removeRestaurant(restaurantId)
                mutable.update { it.copy(confirm = null, removed = true) }
            }
            is RestaurantConfirm.RemoveBranch -> {
                val plan = confirm.check?.archive ?: return
                val action = confirm.staffAction ?: return
                if (!plan.canRemove || confirm.confirmedName.trim() != confirm.branch.name ||
                    (action != "KEEP" && action != "DEACTIVATE") || (action == "DEACTIVATE" && !plan.canDeactivateStaff)) return
                write("${confirm.branch.name} was removed. Sales and payment history were kept.", { it.manage }) { restaurantId ->
                    repository.removeBranch(restaurantId, confirm.branch.id,
                        BranchRemovalRequestDto(action, plan.expectedVersion, confirm.confirmedName.trim(), plan.previewToken))
                    mutable.update { state -> state.copy(confirm = null, branches = state.branches.filter { it.id != confirm.branch.id }) }
                    val acting = session.actingRestaurant.value
                    if (acting?.restaurantId == restaurantId && acting.branchId == confirm.branch.id) {
                        session.enterRestaurant(acting.copy(branchId = null, branchName = null, branchCount = state.value.branches.size))
                    }
                }
            }
            is RestaurantConfirm.BranchStatus -> {
                val open = confirm.status == "ACTIVE"
                write(if (open) "${confirm.branch.name} is open again" else "${confirm.branch.name} is ${branchStatusLabel(confirm.status, false).lowercase()}", { it.manage }) { restaurantId ->
                    val saved = repository.setBranchStatus(restaurantId, confirm.branch.id, StatusRequestDto(open, confirm.status))
                    mutable.update { state -> state.copy(confirm = null, branches = state.branches.map { if (it.id == saved.id) saved else it }) }
                }
            }
            is RestaurantConfirm.RemoveAddress -> write("The address was removed") { restaurantId ->
                repository.removeAddress(restaurantId, null, confirm.address.id)
                mutable.update { state -> state.copy(confirm = null, addresses = state.addresses.filter { it.id != confirm.address.id }) }
            }
            is RestaurantConfirm.RemoveContact -> write("${confirm.contact.fullName} was removed") { restaurantId ->
                repository.removeContact(restaurantId, confirm.contact.id)
                mutable.update { state -> state.copy(confirm = null, contacts = state.contacts.filter { it.id != confirm.contact.id }) }
            }
            is RestaurantConfirm.RemoveTax -> write("The tax details were removed", { it.manage }) { restaurantId ->
                repository.removeTaxProfile(restaurantId, confirm.tax.id)
                mutable.update { state -> state.copy(confirm = null, taxProfiles = state.taxProfiles.filter { it.id != confirm.tax.id }) }
            }
        }
    }

    fun resendOwnerInvite() = write("A new invitation is on its way to the owner", { it.manage }) { restaurantId ->
        repository.resendOwnerInvite(restaurantId)
        refreshInvite(restaurantId)
        // The email goes out a moment later; show when it did.
        actions.launch {
            kotlinx.coroutines.delay(4_000)
            refreshInvite(restaurantId)
        }
    }

    private suspend fun refreshInvite(restaurantId: String) {
        val invite = runCatching { repository.ownerInvite(restaurantId) }.getOrNull() ?: return
        mutable.update { if (it.restaurantId == restaurantId) it.copy(invite = invite) else it }
    }

    fun startAddOwner() {
        val restaurant = state.value.restaurant ?: return
        if (!state.value.canManage) return
        mutable.update { it.copy(ownerStep = OwnerStep(restaurant.id, restaurant.name), notice = null) }
    }

    fun editOwner(change: (OwnerDraft) -> OwnerDraft) = mutable.update { state ->
        val step = state.ownerStep ?: return@update state
        state.copy(ownerStep = step.copy(draft = step.draft.edit(change)))
    }

    fun cancelOwner() {
        if (state.value.ownerStep?.saving != true) mutable.update { it.copy(ownerStep = null) }
    }

    fun saveOwner() {
        if (!state.value.canManage) return
        val step = state.value.ownerStep ?: return
        if (step.saving) return
        if (ownerProblems(step.draft).isNotEmpty()) {
            mutable.update { it.copy(ownerStep = step.copy(draft = step.draft.copy(showProblems = true))) }
            return
        }
        mutable.update { it.copy(ownerStep = step.copy(saving = true, error = null)) }
        actions.launch {
            try {
                session.requireRestaurantAccess { it.manage }
                repository.createOwner(step.restaurantId, step.draft.request())
                mutable.update { it.copy(ownerStep = null, notice = "${step.draft.firstName.trim()} is the owner now. They got an email to set a password.") }
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { state -> state.copy(ownerStep = state.ownerStep?.copy(saving = false, error = platformMessage(e, write = true))) }
            }
        }
    }

    // ---- Transferring ownership ----

    private var candidatesJob: Job? = null

    fun startTransfer() {
        val current = state.value
        val restaurant = current.restaurant ?: return
        val owner = current.owner ?: return
        if (!current.canTransferOwner || current.transfer != null) return
        mutable.update {
            it.copy(transfer = OwnerTransferStep(restaurant.id, restaurant.name, restaurant.version, owner.displayName), notice = null)
        }
        findCandidates("")
    }

    fun transferMode(mode: TransferMode) = updateTransfer { it.copy(mode = mode, error = null) }

    /** Looks people up as they type (a moment after the last key). */
    fun searchCandidates(text: String) {
        updateTransfer { it.copy(search = text.take(100)) }
        findCandidates(text.take(100))
    }

    private fun findCandidates(search: String) {
        val step = state.value.transfer ?: return
        candidatesJob?.cancel()
        updateTransfer { it.copy(loadingCandidates = true, candidatesError = null) }
        candidatesJob = actions.launch {
            try {
                if (search.isNotBlank()) kotlinx.coroutines.delay(250)
                val found = repository.ownerCandidates(step.restaurantId, search.trim())
                updateTransfer { it.copy(candidates = found, loadingCandidates = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateTransfer { it.copy(loadingCandidates = false, candidatesError = platformMessage(e, write = false)) }
            }
        }
    }

    fun chooseCandidate(candidate: OwnerCandidateDto) = updateTransfer { it.copy(chosen = candidate, error = null) }

    fun editTransferInvite(change: (OwnerDraft) -> OwnerDraft) = updateTransfer { it.copy(invite = it.invite.edit(change)) }

    fun transferReason(text: String) = updateTransfer { it.copy(reason = text) }

    fun transferConfirmation(text: String) = updateTransfer { it.copy(confirmedName = text) }

    fun cancelTransfer() {
        if (state.value.transfer?.saving == true) return
        candidatesJob?.cancel()
        mutable.update { it.copy(transfer = null) }
    }

    fun saveTransfer() {
        val step = state.value.transfer ?: return
        if (step.saving || !state.value.canTransferOwner) return
        if (transferProblems(step).isNotEmpty()) {
            updateTransfer { it.copy(showProblems = true, invite = it.invite.copy(showProblems = true)) }
            return
        }
        val token = signIn
        updateTransfer { it.copy(saving = true, error = null) }
        actions.launch {
            try {
                session.requireRestaurantAccess { it.manage }
                val request = OwnerTransferRequestDto(
                    newOwnerUserId = step.chosen?.id.takeIf { step.mode == TransferMode.EXISTING },
                    newOwner = step.invite.request().takeIf { step.mode == TransferMode.INVITE },
                    expectedVersion = step.expectedVersion,
                    reason = step.reason.trim().ifBlank { null }
                )
                val done = repository.transferOwnership(step.restaurantId, request)
                if (token != signIn) return@launch
                val newOwner = done.newOwnerName?.takeIf { it.isNotBlank() } ?: step.newOwnerName
                mutable.update {
                    it.copy(transfer = null, notice = buildString {
                        append("$newOwner owns ${step.restaurantName} now.")
                        if (done.newOwnerInvited) append(" They got an email to set a password.")
                        if (done.previousOwnerDeactivated) append(" ${step.currentOwnerName} was signed out and can no longer sign in.")
                    })
                }
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token != signIn) return@launch
                // Someone changed the restaurant meanwhile: take its new version, so a second look can be confirmed.
                val fresh = if (e is ApiException && e.status == 409) runCatching { repository.restaurant(step.restaurantId) }.getOrNull() else null
                updateTransfer {
                    it.copy(saving = false, error = platformMessage(e, write = true), expectedVersion = fresh?.version ?: it.expectedVersion)
                }
            }
        }
    }

    private fun updateTransfer(change: (OwnerTransferStep) -> OwnerTransferStep) = mutable.update { state ->
        state.transfer?.let { state.copy(transfer = change(it)) } ?: state
    }

    // ---- Branches ----

    fun newBranch() {
        if (!state.value.canManage) return
        mutable.update { it.copy(branch = BranchDraft(country = it.addresses.firstOrNull()?.country.orEmpty(), city = it.addresses.firstOrNull()?.city.orEmpty()), notice = null) }
    }

    fun editBranch(branch: BranchDto) {
        if (!state.value.canEdit) return
        val address = state.value.branchAddresses[branch.id].orEmpty().firstOrNull()
        mutable.update {
            it.copy(branch = BranchDraft(branch.id, branch.name, branch.code, branch.description.orEmpty(), branch.email.orEmpty(), branch.phone.orEmpty(),
                address?.streetLine1.orEmpty(), address?.city.orEmpty(), address?.postalCode.orEmpty(), address?.country.orEmpty(), address?.id, expectedVersion = branch.version), notice = null)
        }
    }

    fun changeBranch(change: (BranchDraft) -> BranchDraft) = mutable.update { state -> state.branch?.let { state.copy(branch = change(it)) } ?: state }

    fun cancelBranch() = mutable.update { it.copy(branch = null) }

    fun saveBranch() {
        if (!state.value.canEdit) return
        val current = state.value
        val draft = current.branch ?: return
        if (branchProblems(draft).isNotEmpty()) {
            mutable.update { it.copy(branch = draft.copy(showProblems = true)) }
            return
        }
        val existing = draft.id?.let { id -> current.branches.firstOrNull { it.id == id } }
        if (draft.id != null && existing == null) {
            mutable.update { it.copy(error = "This branch was removed. Cancel this edit and refresh the list.") }
            return
        }
        write("${draft.name.trim()} was saved", { if (existing == null) it.manage else it.edit }) { restaurantId ->
            val saved = if (existing == null) {
                repository.createBranch(restaurantId, CreateBranchRequestDto(draft.name.trim(), draft.code.trim().ifBlank { null },
                    draft.description.trim().ifBlank { null }, draft.email.trim().ifBlank { null }, draft.phone.trim().ifBlank { null }))
            } else {
                repository.updateBranch(restaurantId, existing.id, UpdateBranchRequestDto(
                    name = if (current.superAdmin) draft.name.trim() else existing.name,
                    code = if (current.superAdmin) draft.code.trim().ifBlank { existing.code } else existing.code,
                    description = draft.description.trim().ifBlank { null }, email = draft.email.trim().ifBlank { null },
                    phone = draft.phone.trim().ifBlank { null }, managerUserId = existing.managerUserId,
                    isActive = existing.isActive, status = existing.status, expectedVersion = draft.expectedVersion))
            }
            // The branch's address: added, changed, or left alone when the form had none.
            var addresses = current.branchAddresses[saved.id].orEmpty()
            if (draft.street.isNotBlank()) {
                val request = AddressRequestDto("PHYSICAL", draft.country.trim(), draft.city.trim(), draft.postalCode.trim().ifBlank { null },
                    draft.street.trim(), null, true)
                val address = repository.saveAddress(restaurantId, saved.id, draft.addressId, request)
                addresses = listOf(address) + addresses.filter { it.id != address.id }
            }
            mutable.update { state ->
                val branches = if (state.branches.any { it.id == saved.id }) state.branches.map { if (it.id == saved.id) saved else it } else state.branches + saved
                state.copy(branch = null, branches = branches, branchAddresses = state.branchAddresses + (saved.id to addresses))
            }
        }
    }

    fun changeBranchRemoval(staffAction: String? = null, confirmedName: String? = null) {
        if (!state.value.canManage || state.value.saving) return
        mutable.update { state ->
            val confirm = state.confirm as? RestaurantConfirm.RemoveBranch ?: return@update state
            val action = staffAction ?: confirm.staffAction
            if (action != null && action !in listOf("KEEP", "DEACTIVATE")) return@update state
            if (action == "DEACTIVATE" && confirm.check?.archive?.canDeactivateStaff != true) return@update state
            state.copy(confirm = confirm.copy(staffAction = action, confirmedName = confirmedName?.take(200) ?: confirm.confirmedName))
        }
    }

    /** Before removing a branch, asks the server what still uses it. */
    fun askRemoveBranch(branch: BranchDto) {
        if (!state.value.canManage) return
        val restaurantId = state.value.restaurantId ?: return
        mutable.update { it.copy(confirm = RestaurantConfirm.RemoveBranch(branch, null), error = null) }
        actions.launch {
            try {
                val check = repository.branchRemovalCheck(restaurantId, branch.id)
                mutable.update { state ->
                    val confirm = state.confirm as? RestaurantConfirm.RemoveBranch ?: return@update state
                    if (confirm.branch.id != branch.id) state else state.copy(confirm = confirm.copy(check = check))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(confirm = null, error = platformMessage(e, write = false)) }
            }
        }
    }

    fun askBranchStatus(branch: BranchDto, status: String) = if (state.value.canManage) mutable.update { it.copy(confirm = RestaurantConfirm.BranchStatus(branch, status)) } else Unit

    // ---- Addresses and contacts ----

    fun newAddress() = if (state.value.canEdit) mutable.update { it.copy(address = AddressDraft(primary = it.addresses.none { a -> a.isPrimary }), notice = null) } else Unit

    fun editAddress(address: AddressDto) = if (state.value.canEdit) mutable.update {
        it.copy(address = AddressDraft(address.id, address.addressType, address.streetLine1, address.streetLine2.orEmpty(), address.city,
            address.postalCode.orEmpty(), address.country, address.isPrimary), notice = null)
    } else Unit

    fun changeAddress(change: (AddressDraft) -> AddressDraft) = mutable.update { state -> state.address?.let { state.copy(address = change(it)) } ?: state }

    fun cancelAddress() = mutable.update { it.copy(address = null) }

    fun saveAddress() {
        if (!state.value.canEdit) return
        val draft = state.value.address ?: return
        if (addressProblems(draft).isNotEmpty()) {
            mutable.update { it.copy(address = draft.copy(showProblems = true)) }
            return
        }
        val request = AddressRequestDto(draft.type, draft.country.trim(), draft.city.trim(), draft.postalCode.trim().ifBlank { null },
            draft.street.trim(), draft.street2.trim().ifBlank { null }, draft.primary)
        write("The address was saved") { restaurantId ->
            repository.saveAddress(restaurantId, null, draft.id, request)
            // Making one primary changes the others too, so read them again.
            val addresses = repository.addresses(restaurantId)
            mutable.update { it.copy(address = null, addresses = addresses) }
        }
    }

    fun askRemoveAddress(address: AddressDto) = if (state.value.canEdit) mutable.update { it.copy(confirm = RestaurantConfirm.RemoveAddress(address)) } else Unit

    fun newContact() = if (state.value.canEdit) mutable.update { it.copy(contact = ContactDraft(primary = it.contacts.none { c -> c.isPrimary }), notice = null) } else Unit

    fun editContact(contact: ContactDto) = if (state.value.canEdit) mutable.update {
        it.copy(contact = ContactDraft(contact.id, contact.contactType, contact.fullName, contact.jobTitle.orEmpty(), contact.email.orEmpty(),
            contact.phone.orEmpty(), contact.isPrimary), notice = null)
    } else Unit

    fun changeContact(change: (ContactDraft) -> ContactDraft) = mutable.update { state -> state.contact?.let { state.copy(contact = change(it)) } ?: state }

    fun cancelContact() = mutable.update { it.copy(contact = null) }

    fun saveContact() {
        if (!state.value.canEdit) return
        val draft = state.value.contact ?: return
        if (contactProblems(draft).isNotEmpty()) {
            mutable.update { it.copy(contact = draft.copy(showProblems = true)) }
            return
        }
        val request = ContactRequestDto(draft.type, draft.fullName.trim(), draft.email.trim().ifBlank { null }, draft.phone.trim().ifBlank { null },
            draft.primary, draft.jobTitle.trim().ifBlank { null })
        write("${draft.fullName.trim()} was saved") { restaurantId ->
            repository.saveContact(restaurantId, draft.id, request)
            val contacts = repository.contacts(restaurantId)
            mutable.update { it.copy(contact = null, contacts = contacts) }
        }
    }

    fun askRemoveContact(contact: ContactDto) = if (state.value.canEdit) mutable.update { it.copy(confirm = RestaurantConfirm.RemoveContact(contact)) } else Unit

    // ---- Logo and receipts ----

    fun changeBranding(change: (BrandingDraft) -> BrandingDraft) = mutable.update { it.copy(brandingDraft = change(it.brandingDraft)) }

    fun resetBranding() = mutable.update { it.copy(brandingDraft = brandingDraftOf(it.branding)) }

    fun saveBranding() {
        if (!state.value.canEdit) return
        val draft = state.value.brandingDraft
        val problem = colorProblem(draft.primaryColor) ?: colorProblem(draft.secondaryColor)
            ?: lengthProblem(draft.receiptHeader, MAX_RECEIPT_TEXT) ?: lengthProblem(draft.receiptFooter, MAX_RECEIPT_TEXT)
        if (problem != null) {
            mutable.update { it.copy(error = problem) }
            return
        }
        write("Logo and receipt texts were saved") { restaurantId ->
            val saved = repository.saveBranding(restaurantId, BrandingRequestDto(draft.primaryColor.trim().ifBlank { null },
                draft.secondaryColor.trim().ifBlank { null }, draft.receiptHeader.trim().ifBlank { null }, draft.receiptFooter.trim().ifBlank { null }))
            mutable.update { it.copy(branding = saved, brandingDraft = brandingDraftOf(saved)) }
        }
    }

    fun uploadLogo(bytes: ByteArray, fileName: String, contentType: String) {
        if (!state.value.canEdit) return
        val restaurantId = state.value.restaurantId ?: return
        if (state.value.uploadingLogo) return
        val token = signIn
        mutable.update { it.copy(uploadingLogo = true, error = null, notice = null) }
        actions.launch {
            try {
                session.requireRestaurantAccess { it.edit }
                val saved = repository.uploadLogo(restaurantId, bytes, fileName, contentType)
                if (token == signIn) mutable.update { it.copy(branding = saved, notice = "The logo was saved") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == signIn) mutable.update { it.copy(error = platformMessage(e, write = true)) }
            } finally {
                mutable.update { it.copy(uploadingLogo = false) }
            }
        }
    }

    fun removeLogo() = write("The logo was removed") { restaurantId ->
        val saved = repository.removeLogo(restaurantId)
        mutable.update { it.copy(branding = saved) }
    }

    fun logoProblem(message: String) = mutable.update { it.copy(error = message) }

    // ---- Legal and tax ----

    fun newTax() {
        if (!state.value.canManage) return
        mutable.update { it.copy(tax = TaxDraft(country = it.addresses.firstOrNull()?.country.orEmpty(), isDefault = it.taxProfiles.none { t -> t.isDefault }), notice = null) }
    }

    fun editTax(tax: TaxProfileDto) {
        if (!state.value.canManage) return
        mutable.update {
            it.copy(tax = TaxDraft(tax.id, tax.country, tax.vatNumber.orEmpty(), tax.taxNumber.orEmpty(), tax.fiscalCode.orEmpty(),
                tax.taxOffice.orEmpty(), tax.isDefault, effectiveFrom = tax.effectiveFrom.orEmpty(), effectiveTo = tax.effectiveTo.orEmpty()), notice = null)
        }
    }

    fun changeTax(change: (TaxDraft) -> TaxDraft) = mutable.update { state -> state.tax?.let { state.copy(tax = change(it)) } ?: state }

    fun cancelTax() = mutable.update { it.copy(tax = null) }

    fun saveTax() {
        if (!state.value.canManage) return
        val draft = state.value.tax ?: return
        if (taxProblems(draft).isNotEmpty()) {
            mutable.update { it.copy(tax = draft.copy(showProblems = true)) }
            return
        }
        val request = TaxProfileRequestDto(draft.country.trim(), draft.fiscalCode.trim().ifBlank { null }, draft.taxNumber.trim().ifBlank { null },
            draft.vatNumber.trim().ifBlank { null }, draft.taxOffice.trim().ifBlank { null }, draft.isDefault,
            draft.effectiveFrom.trim().ifBlank { null }, draft.effectiveTo.trim().ifBlank { null })
        write("The tax details were saved", { it.manage }) { restaurantId ->
            repository.saveTaxProfile(restaurantId, draft.id, request)
            val taxes = repository.taxProfiles(restaurantId)
            mutable.update { it.copy(tax = null, taxProfiles = taxes) }
        }
    }

    fun askRemoveTax(tax: TaxProfileDto) = if (state.value.canManage) mutable.update { it.copy(confirm = RestaurantConfirm.RemoveTax(tax)) } else Unit

    // ---- Currency ----

    fun startCurrencyChange() {
        if (!state.value.canManage) return
        mutable.update { it.copy(currency = CurrencyFlow(), notice = null) }
    }

    fun changeCurrencyFlow(change: (CurrencyFlow) -> CurrencyFlow) = mutable.update { state ->
        val flow = state.currency ?: return@update state
        val changed = change(flow)
        val next = if (changed.newCurrency != flow.newCurrency)
            changed.copy(step = if (changed.newCurrency in ZERO_DECIMAL_CURRENCIES) "1" else "0.01") else changed
        // A different currency, rate or rounding makes the preview out of date.
        val stale = next.newCurrency != flow.newCurrency || next.rateText != flow.rateText || next.step != flow.step
        state.copy(currency = if (stale) next.copy(preview = null, corrections = emptyMap(), error = null) else next)
    }

    fun cancelCurrencyChange() {
        if (state.value.currency?.saving != true) mutable.update { it.copy(currency = null) }
    }

    fun previewCurrency() {
        if (!state.value.canManage) return
        val restaurantId = state.value.restaurantId ?: return
        val flow = state.value.currency ?: return
        val rate = flow.rate
        when {
            flow.newCurrency.isBlank() -> { mutable.update { it.copy(currency = flow.copy(error = "Choose the new currency")) }; return }
            rate == null -> { mutable.update { it.copy(currency = flow.copy(error = "Enter how much 1 ${state.value.restaurant?.currency} is in ${flow.newCurrency} (e.g. 100.5)")) }; return }
        }
        mutable.update { it.copy(currency = flow.copy(loading = true, error = null)) }
        actions.launch {
            try {
                session.requireRestaurantAccess { it.manage }
                val preview = repository.currencyPreview(restaurantId, CurrencyPreviewRequestDto(flow.newCurrency, rate, OrderDecimal(flow.step)))
                mutable.update { state -> state.copy(currency = state.currency?.copy(preview = preview, corrections = emptyMap(), loading = false)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { state -> state.copy(currency = state.currency?.copy(loading = false, error = platformMessage(e, write = false))) }
            }
        }
    }

    fun correctValue(key: String, text: String) = mutable.update { state ->
        val flow = state.currency ?: return@update state
        state.copy(currency = flow.copy(corrections = if (text.isBlank()) flow.corrections - key else flow.corrections + (key to text)))
    }

    fun applyCurrencyChange() {
        if (!state.value.canManage) return
        val restaurantId = state.value.restaurantId ?: return
        val flow = state.value.currency ?: return
        val preview = flow.preview ?: return
        val rate = flow.rate ?: return
        val items = preview.groups.flatMap { it.items }.associateBy { it.key }
        val overrides = mutableListOf<CurrencyOverrideDto>()
        for ((key, text) in flow.corrections) {
            val item = items[key] ?: continue
            val value = text.trim().replace(',', '.')
            if (!Regex("^-?\\d{1,13}(\\.\\d{1,4})?$").matches(value)) {
                mutable.update { it.copy(currency = flow.copy(error = "${item.name}: \"$text\" isn't an amount")) }
                return
            }
            overrides += CurrencyOverrideDto(item.kind, item.id, OrderDecimal(value))
        }
        mutable.update { it.copy(currency = flow.copy(saving = true, error = null)) }
        val token = signIn
        actions.launch {
            writes.withLock {
                try {
                    session.requireRestaurantAccess { it.manage }
                    val done = repository.changeCurrency(restaurantId, CurrencyChangeRequestDto(flow.newCurrency, rate, OrderDecimal(flow.step), overrides, preview.previewToken))
                    if (token != signIn) return@withLock
                    mutable.update { it.copy(currency = null, notice = "The restaurant now uses ${done.newCurrency}. ${done.valueCount} amounts were converted; past sales keep ${done.currentCurrency}.") }
                    load()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (token == signIn) mutable.update { state -> state.copy(currency = state.currency?.copy(saving = false, error = platformMessage(e, write = true))) }
                }
            }
        }
    }

    // ---- Time zone ----

    fun startTimezoneChange() {
        if (!state.value.canManage) return
        mutable.update { it.copy(timezone = TimezoneFlow(), notice = null) }
    }

    fun chooseZone(zone: String) {
        if (!state.value.canManage) return
        val restaurantId = state.value.restaurantId ?: return
        mutable.update { it.copy(timezone = TimezoneFlow(zone = zone, loading = true)) }
        actions.launch {
            try {
                session.requireRestaurantAccess { it.manage }
                val preview = repository.timezonePreview(restaurantId, zone)
                mutable.update { state -> state.copy(timezone = state.timezone?.takeIf { it.zone == zone }?.copy(preview = preview, loading = false) ?: state.timezone) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { state -> state.copy(timezone = state.timezone?.copy(loading = false, error = platformMessage(e, write = false))) }
            }
        }
    }

    fun changeTimezoneFlow(change: (TimezoneFlow) -> TimezoneFlow) = mutable.update { state -> state.timezone?.let { state.copy(timezone = change(it)) } ?: state }

    fun cancelTimezoneChange() {
        if (state.value.timezone?.saving != true) mutable.update { it.copy(timezone = null) }
    }

    fun applyTimezoneChange() {
        if (!state.value.canManage) return
        val restaurantId = state.value.restaurantId ?: return
        val flow = state.value.timezone ?: return
        val preview = flow.preview ?: return
        if (preview.needsConfirmation && !flow.confirmed) {
            mutable.update { it.copy(timezone = flow.copy(error = "Tick that you read what moves")) }
            return
        }
        mutable.update { it.copy(timezone = flow.copy(saving = true, error = null)) }
        actions.launch {
            try {
                session.requireRestaurantAccess { it.manage }
                val saved = repository.changeTimezone(restaurantId, TimezoneChangeRequestDto(flow.zone, flow.keepBookingTimes, flow.confirmed || !preview.needsConfirmation, preview.previewToken))
                mutable.update { it.copy(timezone = null, restaurant = saved, notice = "The restaurant now runs on ${timeZoneLabel(saved.timezone)} time") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { state -> state.copy(timezone = state.timezone?.copy(saving = false, error = platformMessage(e, write = true))) }
            }
        }
    }

    // ---- Shared ----

    private fun write(notice: String?, allowed: (RestaurantAccess) -> Boolean = { it.edit }, action: suspend (restaurantId: String) -> Unit) {
        val current = state.value
        val restaurantId = current.restaurantId ?: return
        if (current.saving || !allowed(restaurantAccess(session.currentUser.value))) return
        val token = signIn
        mutable.update { it.copy(saving = true, error = null, notice = null) }
        actions.launch {
            writes.withLock {
                try {
                    if (token != signIn) return@withLock
                    session.requireRestaurantAccess(allowed)
                    action(restaurantId)
                    if (notice != null && token == signIn) mutable.update { it.copy(notice = notice) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (token != signIn) return@withLock
                    mutable.update { it.copy(error = platformMessage(e, write = true)) }
                    if (e is ApiException && (e.status == 404 || e.status == 409)) load(preserveError = true)
                } finally {
                    mutable.update { it.copy(saving = false) }
                }
            }
        }
    }

    fun clearMessages() = mutable.update { it.copy(error = null, notice = null) }

    override fun onDispose() {
        revision++
        active = false
        work.cancel()
    }
}
