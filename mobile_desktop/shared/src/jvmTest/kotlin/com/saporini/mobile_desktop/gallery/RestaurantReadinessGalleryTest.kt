package com.saporini.mobile_desktop.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.saporini.mobile_desktop.admin.people.PeopleRepository
import com.saporini.mobile_desktop.admin.people.StaffUserDto
import com.saporini.mobile_desktop.auth.data.dto.RestaurantApplicationReceipt
import com.saporini.mobile_desktop.auth.data.dto.RestaurantApplicationStatusView
import com.saporini.mobile_desktop.auth.data.repository.AuthRepository
import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse
import com.saporini.mobile_desktop.auth.ui.apply.ApplicationDraft
import com.saporini.mobile_desktop.auth.ui.apply.ApplicationStatusScreenModel
import com.saporini.mobile_desktop.auth.ui.apply.ApplicationStatusState
import com.saporini.mobile_desktop.auth.ui.apply.ApplyContent
import com.saporini.mobile_desktop.auth.ui.apply.ApplyRestaurantScreenModel
import com.saporini.mobile_desktop.auth.ui.apply.ApplyState
import com.saporini.mobile_desktop.auth.ui.apply.StatusContent
import com.saporini.mobile_desktop.core.session.ActingRestaurant
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.platform.ApplicantDto
import com.saporini.mobile_desktop.platform.BranchCardDto
import com.saporini.mobile_desktop.platform.BranchCardPageDto
import com.saporini.mobile_desktop.platform.BranchListScreenModel
import com.saporini.mobile_desktop.platform.BrandingDto
import com.saporini.mobile_desktop.platform.OwnerCandidateDto
import com.saporini.mobile_desktop.platform.OwnerTransferDto
import com.saporini.mobile_desktop.platform.PageDto
import com.saporini.mobile_desktop.platform.PlatformRepository
import com.saporini.mobile_desktop.platform.RestaurantApplicationDto
import com.saporini.mobile_desktop.platform.RestaurantApplicationsScreenModel
import com.saporini.mobile_desktop.platform.RestaurantDetailsScreenModel
import com.saporini.mobile_desktop.platform.RestaurantDto
import com.saporini.mobile_desktop.platform.RestaurantTab
import com.saporini.mobile_desktop.platform.fakeOf
import com.saporini.mobile_desktop.platform.ui.BranchListContent
import com.saporini.mobile_desktop.platform.ui.OwnerTransferDialog
import com.saporini.mobile_desktop.platform.ui.RestaurantApplicationsContent
import com.saporini.mobile_desktop.platform.ui.RestaurantDetailsContent
import io.ktor.client.HttpClient
import kotlin.test.Test

/** The Restaurant checklist's new screens, rendered with fake data to build/reports/gallery. */
class RestaurantReadinessGalleryTest {
    private val cities = listOf("Tirana", "Durres", "Shkoder", "Vlore", "Korce")
    private val branches = (1..37).map { i ->
        BranchCardDto("b$i", if (i % 3 == 0) "North $i" else "Center $i", code = "B$i", city = cities[i % cities.size], street = "Rruga $i",
            staff = (i * 7 % 13).toLong(), devices = (i % 4).toLong(), phone = "+355 69 000 00$i", active = i % 8 != 0, status = if (i % 8 == 0) "INACTIVE" else "ACTIVE")
    }
    private val applications = listOf(
        RestaurantApplicationDto("a1", "Bistro Lumi", legalName = "Lumi Sh.p.k.", email = "hello@lumi.al", phone = "+355 69 123 4567", currency = "ALL",
            timezone = "Europe/Tirane", submittedAt = "2026-10-21T09:15:00Z", owner = ApplicantDto("Ana", "Hoxha", "ana@lumi.al", "ana.hoxha")),
        RestaurantApplicationDto("a2", "Pasta Fresca", legalName = "Pasta Fresca LLC", email = "ciao@pastafresca.it", phone = "+39 06 1234 5678", currency = "EUR",
            timezone = "Europe/Rome", submittedAt = "2026-10-22T16:40:00Z", owner = ApplicantDto("Marco", "Rossi", "marco@pastafresca.it", "marco"))
    )
    private val repository = fakeOf<PlatformRepository> { name, args ->
        when (name) {
            "restaurant" -> RestaurantDto("r", "Trattoria Roma", legalName = "Trattoria Roma Sh.p.k.", code = "TRATTORIA_ROMA", currency = "ALL",
                timezone = "Europe/Tirane", email = "hello@trattoria.al", phone = "+355 69 222 3333", ownerUserId = "old", version = 6)
            "branchCards" -> {
                val search = args!![1] as String
                val page = args[2] as Int
                val size = args[3] as Int
                val matching = branches.filter { search.isBlank() || it.name.contains(search, true) || it.city.orEmpty().contains(search, true) }.sortedByDescending { it.open }
                BranchCardPageDto(matching.drop(page * size).take(size), page, size, matching.size.toLong(), (matching.size + size - 1) / size,
                    (page + 1) * size < matching.size, branches.size.toLong(), branches.count { it.open }.toLong())
            }
            "applications" -> if (args!![0] == "PENDING") PageDto(applications, 0, 20, 2, 1, false) else PageDto<RestaurantApplicationDto>()
            "branches", "addresses", "contacts", "taxProfiles", "currencyChanges" -> emptyList<Any>()
            "branding" -> BrandingDto()
            "ownerTransfers" -> listOf(OwnerTransferDto("t1", "first", "Gent First", "old", "Olga Owner", transferredByName = "Super Admin",
                reason = "Sold the business", transferredAt = "2025-03-01T10:00:00Z"))
            "ownerCandidates" -> listOf(
                OwnerCandidateDto("m", "Maria", "Manager", "maria@trattoria.al", "maria", listOf("MANAGER")),
                OwnerCandidateDto("l", "Luca", "Lead", "luca@trattoria.al", "luca", listOf("CO_OWNER")))
            else -> error("Unexpected $name")
        }
    }
    private val people = fakeOf<PeopleRepository> { _, _ -> StaffUserDto("old", "olga@trattoria.al", "olga", "Olga", "Owner", emailVerified = true) }

    private fun session() = SessionManager().apply {
        signIn(CurrentUserResponse(id = "admin", email = "admin@example.invalid", username = "admin", firstName = "Super", lastName = "Admin",
            isActive = true, emailVerified = true, phoneVerified = false, roles = listOf("SUPER_ADMIN"),
            permissions = listOf("RESTAURANTS_READ", "RESTAURANTS_UPDATE", "RESTAURANTS_CREATE", "RESTAURANTS_DELETE")))
    }

    @Test fun branchChooserWithSearchAndPaging() = gallery {
        val model = BranchListScreenModel(repository, session())
        settle()
        model.show("r", "Trattoria Roma")
        model.setActive(true)
        settle()
        render("restaurant-branch-chooser", required = listOf("Choose a branch", "37 branches · 33 open", "Search by name, code, city or street")) {
            val state by model.state.collectAsState()
            BranchListContent(state, model, narrow = false, onBack = {}, onEntered = {}, modifier = Modifier.fillMaxSize())
        }
        model.search("north")
        settle()
        render("restaurant-branch-chooser-search", width = 420, height = 900, required = listOf("12 branches found")) {
            val state by model.state.collectAsState()
            BranchListContent(state, model, narrow = true, onBack = {}, onEntered = {}, modifier = Modifier.fillMaxSize())
        }
        model.onDispose()
    }

    @Test fun applicationsQueueAndRejectDialog() = gallery {
        val model = RestaurantApplicationsScreenModel(repository, session())
        settle()
        model.setActive(true)
        settle()
        render("restaurant-applications", required = listOf("Restaurant applications", "2 applications are waiting for review", "Bistro Lumi", "Approve")) {
            val state by model.state.collectAsState()
            RestaurantApplicationsContent(state, model, narrow = false, onBack = {})
        }
        model.startReject(applications.first())
        model.editReason("We need your business licence (NIPT) to approve the restaurant.")
        render("restaurant-applications-reject", required = listOf("Reject Bistro Lumi?", "Reason")) {
            val state by model.state.collectAsState()
            RestaurantApplicationsContent(state, model, narrow = false, onBack = {})
        }
        model.onDispose()
    }

    @Test fun ownerPanelAndTransferDialog() = gallery {
        val session = session().apply { enterRestaurant(ActingRestaurant("r", "Trattoria Roma", "b1", "Center 1")) }
        val model = RestaurantDetailsScreenModel(repository, people, session)
        settle()
        model.setActive(true)
        settle()
        render("restaurant-owner-panel", height = 1500, required = listOf("Transfer ownership", "Owner history", "Gent First → Olga Owner")) {
            val state by model.state.collectAsState()
            RestaurantDetailsContent(state, model, RestaurantTab.DETAILS, {}, showTabs = false)
        }
        // As Admin Hub → Restaurant info shows it: the restaurant on top, then the tabs.
        render("restaurant-info", height = 1500, required = listOf("Trattoria Roma", "Who the restaurant is", "How guests reach you", "How guests see it",
            "Remove the restaurant")) {
            val state by model.state.collectAsState()
            RestaurantDetailsContent(state, model, RestaurantTab.DETAILS, {}, showTabs = true)
        }
        render("restaurant-info-phone", 390, 2200) {
            val state by model.state.collectAsState()
            RestaurantDetailsContent(state, model, RestaurantTab.DETAILS, {}, showTabs = true)
        }
        // The responsive pass: phones (360, 412), small tablets (600, 768) and a tablet (1024).
        listOf(360 to 2400, 412 to 2400, 600 to 2100, 768 to 1900, 1024 to 1700).forEach { (width, height) ->
            render("size-restaurant-$width", width, height, required = listOf("Trattoria Roma", "Who the restaurant is", "How guests see it", "Remove restaurant")) {
                val state by model.state.collectAsState()
                RestaurantDetailsContent(state, model, RestaurantTab.DETAILS, {}, showTabs = true)
            }
        }
        listOf(RestaurantTab.BRANDING to "branding", RestaurantTab.PLACES to "places", RestaurantTab.MONEY to "money").forEach { (tab, name) ->
            render("size-restaurant-$name-360", 360, 1300) {
                val state by model.state.collectAsState()
                RestaurantDetailsContent(state, model, tab, {}, showTabs = true)
            }
        }
        model.startTransfer()
        settle()
        model.chooseCandidate(OwnerCandidateDto("m", "Maria", "Manager", "maria@trattoria.al", "maria", listOf("MANAGER")))
        model.transferReason("Olga sold the business to Maria")
        render("restaurant-owner-transfer", height = 1100, required = listOf("Transfer Trattoria Roma", "Maria Manager becomes the owner", "Olga Owner loses the restaurant")) {
            val state by model.state.collectAsState()
            state.transfer?.let { OwnerTransferDialog(it, model) }
        }
        model.onDispose()
    }

    @Test fun applicantScreens() = gallery {
        val auth = AuthRepository(HttpClient(), { listOf("http://127.0.0.1:1") }, {})
        val apply = ApplyRestaurantScreenModel(auth)
        val draft = ApplicationDraft(name = "Bistro Lumi", legalName = "Lumi Sh.p.k.", email = "hello@lumi", phone = "", currency = "ALL",
            firstName = "Ana", lastName = "Hoxha", ownerEmail = "ana@lumi.al", username = "ana.hoxha", showProblems = true)
        render("restaurant-apply-form", height = 1400, required = listOf("Open a restaurant", "This isn't an email address", "Enter a phone number")) {
            page { ApplyContent(ApplyState(draft = draft), apply, wide = true, onCheck = {}) }
        }
        render("restaurant-apply-sent", height = 900, required = listOf("Application sent", "7KQ2M-X9D4T-PW3HB-R8N6C", "Copy the code")) {
            page {
                ApplyContent(ApplyState(receipt = RestaurantApplicationReceipt("a", "Bistro Lumi", statusCode = "7KQ2M-X9D4T-PW3HB-R8N6C",
                    ownerEmail = "ana@lumi.al")), apply, wide = true, onCheck = {})
            }
        }
        val status = ApplicationStatusScreenModel(auth)
        render("restaurant-application-rejected", width = 420, height = 900, required = listOf("Bistro Lumi wasn't approved", "We need your business licence")) {
            page {
                StatusContent(ApplicationStatusState(code = "7KQ2M-X9D4T-PW3HB-R8N6C", view = RestaurantApplicationStatusView("a", "Bistro Lumi", "REJECTED",
                    rejectionReason = "We need your business licence (NIPT).", submittedAt = "2026-10-21T09:15:00Z", reviewedAt = "2026-10-22T11:00:00Z",
                    message = "Your application was not approved. You can apply again once the reason is sorted out.")), status, onSignIn = {})
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun page(content: @androidx.compose.runtime.Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
        Column(Modifier.fillMaxSize().background(Color(0xFFFCFCFB)).verticalScroll(rememberScrollState()).padding(24.dp).widthIn(max = 760.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp), content = content)
    }
}
