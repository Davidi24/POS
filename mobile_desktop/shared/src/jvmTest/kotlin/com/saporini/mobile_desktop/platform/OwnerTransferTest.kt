@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.saporini.mobile_desktop.platform

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ImageComposeScene
import com.saporini.mobile_desktop.admin.people.PeopleRepository
import com.saporini.mobile_desktop.admin.people.StaffUserDto
import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.ActingRestaurant
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.platform.ui.RestaurantDetailsContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Super Admin giving a restaurant to a new owner from the Restaurant app's Owner panel. */
class OwnerTransferTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val disposals = mutableListOf<() -> Unit>()
    private val permissions = listOf("RESTAURANTS_READ", "RESTAURANTS_UPDATE", "RESTAURANTS_CREATE", "RESTAURANTS_DELETE")
    private var restaurant = RestaurantDto("r", "Trattoria", legalName = "Trattoria LLC", code = "TRATTORIA", currency = "EUR",
        timezone = "Europe/Tirane", email = "hello@trattoria.example", phone = "+355691234567", ownerUserId = "old", version = 4)
    private val oldOwner = StaffUserDto("old", "old@owner.example", "old.owner", "Olga", "Owner", emailVerified = true)
    private val maria = OwnerCandidateDto("maria", "Maria", "Manager", "maria@trattoria.example", "maria", listOf("MANAGER"))
    private val luca = OwnerCandidateDto("luca", "Luca", "Waiter", "luca@trattoria.example", "luca", listOf("WAITER"))
    private val searches = mutableListOf<String>()
    private val transfers = mutableListOf<OwnerTransferRequestDto>()
    private var transferFailure: ApiException? = null
    private var history = emptyList<OwnerTransferDto>()
    private val repository = fakeOf<PlatformRepository> { name, args ->
        when (name) {
            "restaurant" -> restaurant
            "branches", "addresses", "contacts", "taxProfiles", "currencyChanges" -> emptyList<Any>()
            "branding" -> BrandingDto()
            "ownerTransfers" -> history
            "ownerCandidates" -> {
                val search = args!![1] as String
                searches += search
                listOf(maria, luca).filter { search.isBlank() || it.displayName.contains(search, true) }
            }
            "transferOwnership" -> {
                val request = args!![1] as OwnerTransferRequestDto
                transfers += request
                transferFailure?.let { throw it }
                restaurant = restaurant.copy(ownerUserId = request.newOwnerUserId ?: "invited", version = restaurant.version + 1)
                history = listOf(OwnerTransferDto("t1", "old", "Olga Owner", restaurant.ownerUserId!!, "Maria Manager", request.newOwner != null,
                    previousOwnerDeactivated = true, sessionsRevoked = 2, reason = request.reason, transferredByName = "Super Admin",
                    transferredAt = "2026-10-24T10:00:00Z"))
                history.first()
            }
            else -> error("Unexpected $name")
        }
    }
    private val people = fakeOf<PeopleRepository> { name, args ->
        when (name) {
            "person" -> if (args!![0] == "old") oldOwner else StaffUserDto(args[0] as String, "new@owner.example", "new", "Maria", "Manager", emailVerified = true)
            else -> error("Unexpected people call $name")
        }
    }

    @BeforeTest fun before() = Dispatchers.setMain(dispatcher)

    @AfterTest fun after() {
        disposals.forEach { it() }
        Dispatchers.resetMain()
    }

    private fun session(role: String) = SessionManager().apply {
        signIn(CurrentUserResponse(id = "admin", email = "admin@example.invalid", username = "admin", firstName = "Super", lastName = "Admin",
            isActive = true, emailVerified = true, phoneVerified = false, roles = listOf(role), permissions = permissions))
        enterRestaurant(ActingRestaurant("r", "Trattoria", "b", "Main"))
    }

    private fun model(role: String = "SUPER_ADMIN") = RestaurantDetailsScreenModel(repository, people, session(role)).also { model ->
        disposals += { model.onDispose() }
        scheduler.runCurrent()
        model.setActive(true)
        scheduler.runCurrent()
    }

    @Test fun onlyTheSuperAdminCanStartATransfer() {
        val co = model("CO_SUPER_ADMIN")
        assertFalse(co.state.value.canTransferOwner)
        co.startTransfer(); scheduler.runCurrent()
        assertNull(co.state.value.transfer)

        val admin = model()
        assertTrue(admin.state.value.canTransferOwner)
        admin.startTransfer(); scheduler.runCurrent()
        val step = admin.state.value.transfer
        assertNotNull(step)
        assertEquals("Olga Owner", step.currentOwnerName)
        assertEquals(4, step.expectedVersion)
        assertEquals(listOf(maria, luca), step.candidates)
        assertEquals(listOf(""), searches)
    }

    @Test fun nothingIsSentUntilAPersonIsChosenAndTheNameIsTyped() {
        val model = model()
        model.startTransfer(); scheduler.runCurrent()
        model.saveTransfer(); scheduler.runCurrent()
        val problems = transferProblems(model.state.value.transfer!!)
        assertEquals(setOf("person", "confirm"), problems.keys)
        assertTrue(model.state.value.transfer!!.showProblems)
        assertTrue(transfers.isEmpty())

        model.chooseCandidate(maria)
        model.transferConfirmation("trattoria")
        assertEquals(setOf("confirm"), transferProblems(model.state.value.transfer!!).keys)
        model.transferReason("x".repeat(MAX_TRANSFER_REASON + 1))
        model.transferConfirmation("  Trattoria ")
        assertEquals(setOf("reason"), transferProblems(model.state.value.transfer!!).keys)
        model.saveTransfer(); scheduler.runCurrent()
        assertTrue(transfers.isEmpty())
    }

    @Test fun givingItToAStaffMemberSendsTheirIdTheVersionAndTheReasonThenReloads() {
        val model = model()
        model.startTransfer(); scheduler.runCurrent()
        model.searchCandidates("mar")
        scheduler.advanceTimeBy(300); scheduler.runCurrent()
        assertEquals(listOf(maria), model.state.value.transfer!!.candidates)
        model.chooseCandidate(maria)
        model.transferReason("  Sold the business  ")
        model.transferConfirmation("Trattoria")
        model.saveTransfer()
        assertTrue(model.state.value.transfer!!.saving)
        scheduler.runCurrent()

        assertEquals(OwnerTransferRequestDto(newOwnerUserId = "maria", newOwner = null, expectedVersion = 4, reason = "Sold the business"), transfers.single())
        assertNull(model.state.value.transfer)
        val notice = model.state.value.notice.orEmpty()
        assertTrue(notice.contains("owns Trattoria now"), notice)
        assertTrue(notice.contains("Olga Owner was signed out"), notice)
        assertEquals("maria", model.state.value.restaurant?.ownerUserId)
        assertEquals(1, model.state.value.ownerTransfers.size)
    }

    @Test fun invitingANewPersonSendsTheirDetailsInstead() {
        val model = model()
        model.startTransfer(); scheduler.runCurrent()
        model.transferMode(TransferMode.INVITE)
        model.transferConfirmation("Trattoria")
        model.saveTransfer(); scheduler.runCurrent()
        val problems = transferProblems(model.state.value.transfer!!)
        assertTrue(problems.keys.containsAll(listOf("invite.firstName", "invite.lastName", "invite.email", "invite.username")), problems.toString())
        assertTrue(transfers.isEmpty())

        model.editTransferInvite { it.copy(firstName = "Nina", lastName = "New", email = "nina@new.example") }
        assertEquals("nina", model.state.value.transfer!!.invite.username)
        model.saveTransfer(); scheduler.runCurrent()
        val sent = transfers.single()
        assertNull(sent.newOwnerUserId)
        assertEquals(OwnerInviteDto("nina@new.example", "nina", "Nina", "New"), sent.newOwner)
        assertNull(sent.reason)
        assertTrue(model.state.value.notice.orEmpty().contains("got an email to set a password"))
    }

    @Test fun aConflictKeepsTheDialogWithTheReasonAndTakesTheNewVersion() {
        val model = model()
        model.startTransfer(); scheduler.runCurrent()
        model.chooseCandidate(maria)
        model.transferConfirmation("Trattoria")
        restaurant = restaurant.copy(version = 9)
        transferFailure = ApiException(409, "The restaurant changed since you opened it.")
        model.saveTransfer(); scheduler.runCurrent()
        val step = model.state.value.transfer!!
        assertFalse(step.saving)
        assertEquals("The restaurant changed since you opened it.", step.error)
        assertEquals(9, step.expectedVersion)

        transferFailure = null
        model.saveTransfer(); scheduler.runCurrent()
        assertEquals(9, transfers.last().expectedVersion)
        assertNull(model.state.value.transfer)
    }

    @Test fun cancellingWhileSavingIsIgnored() {
        val model = model()
        model.startTransfer(); scheduler.runCurrent()
        model.chooseCandidate(maria)
        model.transferConfirmation("Trattoria")
        model.saveTransfer()
        model.cancelTransfer()
        assertNotNull(model.state.value.transfer)
        scheduler.runCurrent()
        assertNull(model.state.value.transfer)
    }

    @Test fun theOwnerPanelOffersTheTransferAndTheDialogSpellsOutWhatHappens() {
        val model = model()
        val scene = ImageComposeScene(1400, 1300, coroutineContext = dispatcher) {
            val state by model.state.collectAsState()
            ScenePage { RestaurantDetailsContent(state, model, RestaurantTab.DETAILS, {}, showTabs = false) }
        }
        try {
            scene.settle(scheduler)
            scene.click("Transfer ownership")
            scene.settle(scheduler)
            val text = scene.labels()
            assertTrue(text.contains("Transfer Trattoria"), text)
            assertTrue(text.contains("Someone in the POS"), text)
            assertTrue(text.contains("Maria Manager"), text)
            assertTrue(text.contains("Olga Owner loses the restaurant"), text)
            assertTrue(text.contains("Type the restaurant's name to confirm"), text)
        } finally {
            scene.close()
        }
    }
}
