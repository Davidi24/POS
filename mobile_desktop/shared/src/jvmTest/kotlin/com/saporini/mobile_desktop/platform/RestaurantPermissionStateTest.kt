@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.saporini.mobile_desktop.platform

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.saporini.mobile_desktop.admin.people.PeopleRepository
import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.ActingRestaurant
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.theme.SaporiniTheme
import com.saporini.mobile_desktop.platform.ui.RestaurantDetailsContent
import com.saporini.mobile_desktop.platform.ui.BranchListContent
import com.saporini.mobile_desktop.platform.ui.RestaurantListContent
import java.lang.reflect.Proxy
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.*

class RestaurantPermissionStateTest {
    private val scheduler=TestCoroutineScheduler()
    private val dispatcher=StandardTestDispatcher(scheduler)
    private val writes=mutableListOf<String>()
    private val disposals=mutableListOf<()->Unit>()
    private val read=listOf("RESTAURANTS_READ")
    private val edit=read+"RESTAURANTS_UPDATE"
    private val all=edit+listOf("RESTAURANTS_CREATE","RESTAURANTS_DELETE")
    private val restaurant=RestaurantDto("r","Restaurant",legalName="Restaurant LLC",code="R",currency="EUR",timezone="Europe/Tirane",phone="+355691234567",email="r@example.invalid")
    private val branch=BranchDto("b","Main",code="MAIN")
    private val card=RestaurantCardDto("r","Restaurant",branches=1)
    private val branchCard=BranchCardDto("b","Main")
    private val people=fake<PeopleRepository> { name,_ -> error("Unexpected people request $name") }
    private val repository=fake<PlatformRepository> { name,args ->
        when(name) {
            "restaurant" -> restaurant.copy(id=args!![0] as String)
            "cards" -> PageDto(items=listOf(card),totalElements=1)
            "branches" -> listOf(branch)
            "branchCards" -> BranchCardPageDto(listOf(branchCard),totalElements=1,allBranches=1,openBranches=1)
            "branch" -> branch
            "applications" -> PageDto<RestaurantApplicationDto>()
            "ownerTransfers","ownerCandidates" -> emptyList<Any>()
            "addresses","contacts","taxProfiles","currencyChanges" -> emptyList<Any>()
            "branding" -> BrandingDto()
            "branchRemovalCheck" -> BranchRemovalCheckDto(canRemove=true)
            "updateRestaurant","createRestaurant","createOwner","updateOwner","setRestaurantStatus","changeTimezone" -> {writes+=name;restaurant}
            "createBranch","updateBranch","setBranchStatus" -> {writes+=name;branch}
            "uploadCover","removeCover" -> {writes+=name;CoverDto("photo")}
            "uploadLogo","removeLogo","saveBranding" -> {writes+=name;BrandingDto()}
            else -> {writes+=name;Unit}
        }
    }
    @BeforeTest fun before() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun after() { disposals.forEach { it() };stopKoin();Dispatchers.resetMain() }
    private fun user(permissions:List<String>,role:String="CO_SUPER_ADMIN",id:String="u")=CurrentUserResponse(
        id=id,restaurantId=if(role=="OWNER") "r" else null,email="u@example.invalid",username="u",firstName="User",lastName="Test",
        isActive=true,emailVerified=true,phoneVerified=false,roles=listOf(role),permissions=permissions)
    private fun session(permissions:List<String>,role:String="CO_SUPER_ADMIN")=SessionManager().apply {
        signIn(user(permissions,role));if(role!="OWNER")enterRestaurant(ActingRestaurant("r","Restaurant","b","Main"))
    }
    private fun details(s:SessionManager,repo:PlatformRepository=repository)=RestaurantDetailsScreenModel(repo,people,s).also {
        disposals+={it.onDispose()};scheduler.runCurrent();it.setActive(true);scheduler.runCurrent()
        assertTrue(it.state.value.loaded)
    }
    private fun list(s:SessionManager)=RestaurantListScreenModel(repository,people,s).also {
        disposals+={it.onDispose()};scheduler.runCurrent();it.setActive(true);scheduler.runCurrent()
    }
    private fun branches(s:SessionManager)=BranchListScreenModel(repository,s).also {
        disposals+={it.onDispose()};scheduler.runCurrent();it.show("r","Restaurant");it.setActive(true);scheduler.runCurrent()
    }
    @Test fun roleNeverOverridesADeniedPermission() {
        for(role in listOf("SUPER_ADMIN","CO_SUPER_ADMIN","OWNER","CUSTOM")) {
            val a=restaurantAccess(user(read,role));assertTrue(a.read);assertFalse(a.edit);assertFalse(a.manage);assertFalse(a.create);assertFalse(a.delete)
        }
        assertEquals(RestaurantAccess(false,false,false,false,false),restaurantAccess(null))
        assertFalse(restaurantAccess(user(all).copy(isActive=false)).edit)
        assertTrue(restaurantAccess(user(edit,"OWNER")).edit)
        assertFalse(restaurantAccess(user(all,"OWNER")).manage)
    }
    @Test fun readOnlyDetailsCannotOpenOrExecuteAnyWrite() {
        val model=details(session(read))
        model.editDetails();model.newBranch();model.editBranch(branch);model.askStatus(false,"INACTIVE");model.askRemoveRestaurant()
        model.newAddress();model.newContact();model.newTax();model.startCurrencyChange();model.startTimezoneChange();model.startAddOwner()
        model.askRemoveBranch(branch);model.askBranchStatus(branch,"INACTIVE");model.askRemoveTax(TaxProfileDto("tax"))
        model.uploadLogo(byteArrayOf(1),"photo.png","image/png");model.removeLogo();model.saveBranding();model.resendOwnerInvite();model.confirm()
        scheduler.runCurrent()
        val s=model.state.value
        assertNull(s.details);assertNull(s.branch);assertNull(s.address);assertNull(s.contact);assertNull(s.tax);assertNull(s.currency);assertNull(s.timezone);assertNull(s.ownerStep);assertNull(s.confirm)
        assertTrue(writes.isEmpty(),writes.toString())
    }
    @Test fun createUpdateAndDeleteAreIndependent() {
        val createOnly=list(session(read+"RESTAURANTS_CREATE"))
        assertTrue(createOnly.state.value.canCreate);assertFalse(createOnly.state.value.canManage)
        createOnly.startEdit(card);createOnly.addOwner(card);createOnly.addBranch(card);createOnly.removeCover(card);createOnly.startReorder()
        scheduler.runCurrent();assertNull(createOnly.state.value.edit);assertNull(createOnly.state.value.owner);assertNull(createOnly.state.value.branch)
        val updateOnly=list(session(edit));assertFalse(updateOnly.state.value.canCreate);assertTrue(updateOnly.state.value.canManage)
        updateOnly.addBranch(card);assertNotNull(updateOnly.state.value.branch)
        val deleteOnly=details(session(read+"RESTAURANTS_DELETE"));assertFalse(deleteOnly.state.value.canEdit);assertTrue(deleteOnly.state.value.canDelete)
        deleteOnly.askRemoveRestaurant();assertNotNull(deleteOnly.state.value.confirm)
        assertTrue(writes.isEmpty())
    }
    @Test fun ownerCanEditGuestDetailsButCannotOpenPlatformChanges() {
        val model=details(session(edit,"OWNER"))
        model.editDetails();assertNotNull(model.state.value.details)
        model.newBranch();model.newTax();model.startCurrencyChange();model.startTimezoneChange();model.askStatus(false,"INACTIVE")
        assertNull(model.state.value.branch);assertNull(model.state.value.tax);assertNull(model.state.value.currency);assertNull(model.state.value.timezone);assertNull(model.state.value.confirm)
        model.changeDetails { it.copy(phone="+355691112222") };model.saveDetails();scheduler.runCurrent()
        assertEquals(listOf("updateRestaurant"),writes)
    }
    @Test fun revocationClosesOpenDialogsAcrossAllThreeModels() {
        val s=session(all);val d=details(s);val l=list(s);val b=branches(s)
        d.editDetails();l.addOwner(card);b.addBranch()
        assertNotNull(d.state.value.details);assertNotNull(l.state.value.owner);assertNotNull(b.state.value.branch)
        s.signIn(user(read));scheduler.runCurrent()
        assertNull(d.state.value.details);assertNull(l.state.value.owner);assertNull(b.state.value.branch)
        assertFalse(d.state.value.canEdit);assertFalse(l.state.value.canManage);assertFalse(b.state.value.canChangeCover)
        d.saveDetails();l.saveOwner();b.saveBranch();b.removeCover(branchCard);scheduler.runCurrent()
        assertTrue(writes.isEmpty())
    }
    @Test fun pollingRefreshesPermissionChangesWhileDialogIsOpen() {
        val s=session(all);var server=user(all);s.setUserLoader {server}
        val model=details(s);model.editDetails();assertNotNull(model.state.value.details)
        server=user(read);scheduler.advanceTimeBy(15_001);scheduler.runCurrent()
        assertNull(model.state.value.details);assertFalse(model.state.value.canEdit);assertTrue(writes.isEmpty())
    }
    @Test fun saveRechecksServerPermissionsBeforeTheNextPoll() {
        val s=session(all);var server=user(all);s.setUserLoader {server}
        val model=details(s);model.editDetails();server=user(read)
        model.saveDetails();scheduler.runCurrent()
        assertTrue(writes.isEmpty());assertFalse(model.state.value.canEdit);assertNull(model.state.value.details)
    }
    @Test fun expiredSessionClearsPrivateDataAndPreventsPendingSave() {
        val s=session(all);val model=details(s);model.editDetails()
        s.setUserLoader {throw ApiException(401,"Session expired")}
        model.saveDetails();scheduler.runCurrent()
        assertFalse(s.isAuthenticated.value);assertNull(model.state.value.restaurant);assertNull(model.state.value.details);assertTrue(writes.isEmpty())
    }
    @Test fun switchingRestaurantClearsItsPreviousDraft() {
        val s=session(all);val model=details(s);model.editDetails()
        s.enterRestaurant(ActingRestaurant("other","Other"));scheduler.runCurrent()
        assertNull(model.state.value.details);assertEquals("other",model.state.value.restaurantId);assertEquals("other",model.state.value.restaurant?.id)
    }
    @Test fun oldLoadCannotRestoreDataAfterSignOut() {
        val s=session(all);val pending=CompletableDeferred<RestaurantDto>()
        val repo=object:PlatformRepository by repository {override suspend fun restaurant(restaurantId:String)=pending.await()}
        val model=RestaurantDetailsScreenModel(repo,people,s);disposals+={model.onDispose()}
        scheduler.runCurrent();model.setActive(true);scheduler.runCurrent();assertTrue(model.state.value.loading)
        s.signOut();scheduler.runCurrent();pending.complete(restaurant);scheduler.runCurrent()
        assertNull(model.state.value.restaurant);assertFalse(model.state.value.loading)
    }
    @Test fun screenHidesProtectedButtonsAndClosesRevokedEditDialog() {
        val s=session(all);val model=details(s)
        startKoin {modules(module {single {s}})}
        val scene=ImageComposeScene(1280,1000,coroutineContext=dispatcher) {
            val state by model.state.collectAsState()
            SaporiniTheme {RestaurantDetailsContent(state,model,state.tab,model::tab,true)}
        }
        try {
            render(scene);assertTrue(labels(scene).contains("Edit details"));click(scene,"Edit details");render(scene)
            assertNotNull(model.state.value.details)
            assertTrue(clickLabels(scene).contains("Save"))
            s.signIn(user(read));render(scene)
            assertNull(model.state.value.details)
            for(text in listOf("Edit details","Add the owner","Suspend","Remove restaurant")) assertFalse(clickLabels(scene).contains(text),text)
            model.tab(RestaurantTab.MONEY);render(scene)
            assertFalse(clickLabels(scene).contains("Change currency"));assertFalse(clickLabels(scene).contains("Change time zone"))
            model.tab(RestaurantTab.BRANCHES);render(scene);assertFalse(clickLabels(scene).contains("Add branch"))
            model.tab(RestaurantTab.LEGAL);render(scene);assertFalse(clickLabels(scene).contains("Add tax details"))
        } finally {scene.close()}
    }
    @Test fun branchScreenHidesAddAndPhotoActionsFromReadOnlyUser() {
        val s=session(read);val model=branches(s)
        val scene=ImageComposeScene(1280,900,coroutineContext=dispatcher) {
            val state by model.state.collectAsState()
            SaporiniTheme {BranchListContent(state,model,false,{}, {})}
        }
        try {render(scene);assertTrue(labels(scene).contains("Main"));assertFalse(clickLabels(scene).contains("Add a branch"));assertFalse(clickLabels(scene).contains("Change picture"))}
        finally {scene.close()}
    }
    @Test fun listScreenSeparatesCreateFromEditActions() {
        val s=session(read+"RESTAURANTS_CREATE");val model=list(s)
        val scene=ImageComposeScene(1280,1000,coroutineContext=dispatcher) {
            val state by model.state.collectAsState()
            SaporiniTheme {RestaurantListContent(state,model,false,{}, {}, {})}
        }
        try {render(scene);assertTrue(labels(scene).contains("Create a new restaurant"));assertFalse(clickLabels(scene).contains("Add branch"));assertFalse(clickLabels(scene).contains("Add owner"));assertFalse(clickLabels(scene).contains("Edit"))}
        finally {scene.close()}
    }
    @Test fun revokedReadAccessClearsAllScreensAndShowsAnExplanation() {
        val s=session(all);val d=details(s);val l=list(s);val b=branches(s)
        l.startEdit(card);b.addBranch();d.editDetails();scheduler.runCurrent()
        s.signIn(user(emptyList()));scheduler.runCurrent()
        assertNull(d.state.value.restaurant);assertTrue(l.state.value.cards.isEmpty());assertTrue(b.state.value.cards.isEmpty())
        assertNull(l.state.value.edit);assertNull(b.state.value.branch);assertNull(d.state.value.details)
        val listScene=ImageComposeScene(1280,900,coroutineContext=dispatcher) { SaporiniTheme {RestaurantListContent(l.state.value,l,false,{}, {}, {})} }
        val branchScene=ImageComposeScene(1280,900,coroutineContext=dispatcher) { SaporiniTheme {BranchListContent(b.state.value,b,false,{}, {})} }
        try {
            render(listScene);render(branchScene)
            assertTrue(labels(listScene).contains("Access unavailable"));assertTrue(labels(branchScene).contains("Access unavailable"))
        } finally {listScene.close();branchScene.close()}
    }
    @Test fun expirationClosesDialogsAndClearsBothPickerScreens() {
        val s=session(all);val l=list(s);val b=branches(s)
        l.addOwner(card);b.addBranch()
        s.setUserLoader {throw ApiException(401,"Expired")}
        scheduler.advanceTimeBy(15_001);scheduler.runCurrent()
        assertNull(s.currentUser.value);assertTrue(l.state.value.cards.isEmpty());assertTrue(b.state.value.cards.isEmpty())
        assertNull(l.state.value.owner);assertNull(b.state.value.branch);assertFalse(l.state.value.canManage);assertFalse(b.state.value.canChangeCover)
    }
    @Test fun failedPermissionRefreshBlocksSavingAndCanBeRetried() {
        val s=session(all);val model=details(s);model.editDetails()
        var offline=true
        s.setUserLoader {if(offline) throw ApiException(503,"Temporarily unavailable") else user(all)}
        model.saveDetails();scheduler.runCurrent()
        assertTrue(writes.isEmpty());assertNotNull(model.state.value.details);assertNotNull(model.state.value.error)
        offline=false;model.saveDetails();scheduler.runCurrent()
        assertEquals(listOf("updateRestaurant"),writes);assertNull(model.state.value.details)
    }
    @Test fun oldPermissionResponseCannotReplaceANewLoginOrUndoLogout() {
        for(logOut in listOf(false,true)) {
            val s=session(all);val pending=CompletableDeferred<CurrentUserResponse>()
            s.setUserLoader {pending.await()}
            val job=CoroutineScope(dispatcher).launch {s.refreshPermissions()}
            scheduler.runCurrent()
            if(logOut) s.signOut() else s.signIn(user(read,id="new-user"))
            pending.complete(user(all));scheduler.runCurrent()
            if(logOut) assertNull(s.currentUser.value) else {
                assertEquals("new-user",s.currentUser.value?.id)
                assertFalse(restaurantAccess(s.currentUser.value).edit)
            }
            assertTrue(job.isCompleted)
        }
    }
    @Test fun createOnlySetupFinishesWithoutOpeningAnUnauthorizedOwnerStep() {
        val s=session(read+"RESTAURANTS_CREATE");val model=list(s)
        model.startCreate();model.editDraft {it.copy(name="Created",legalName="Created LLC",phone="+355691234567",email="new@example.invalid",currency="EUR",timezone="Europe/Tirane")}
        model.saveCreate();scheduler.runCurrent()
        assertEquals(listOf("createRestaurant"),writes);assertNotNull(model.state.value.created)
        model.continueToOwner();assertNull(model.state.value.created);assertNull(model.state.value.owner)
    }
    @Test fun taxEditPreservesDatesAndFailureKeepsTheDraftForRetry() {
        var attempts=0; var saved:TaxProfileRequestDto?=null
        val original=TaxProfileDto("tax",country="AL",vatNumber="VAT",isDefault=true,
            effectiveFrom="2026-01-01T00:00:00+01:00",effectiveTo="2027-01-01T00:00:00+01:00")
        val repo=object:PlatformRepository by repository {
            override suspend fun saveTaxProfile(restaurantId:String,taxProfileId:String?,request:TaxProfileRequestDto):TaxProfileDto {
                attempts++;if(attempts==1)throw ApiException(503,"Storage unavailable")
                saved=request;return original
            }
        }
        val model=details(session(all),repo);model.editTax(original)
        model.changeTax {it.copy(vatNumber="CHANGED")};model.saveTax();scheduler.runCurrent()
        assertNotNull(model.state.value.tax);assertNotNull(model.state.value.error)
        assertEquals(original.effectiveFrom,model.state.value.tax?.effectiveFrom)
        model.saveTax();scheduler.runCurrent()
        assertEquals(2,attempts);assertNull(model.state.value.tax)
        assertEquals(original.effectiveFrom,saved?.effectiveFrom);assertEquals(original.effectiveTo,saved?.effectiveTo)
        assertEquals("CHANGED",saved?.vatNumber)
    }
    @Test fun taxDatesRejectMalformedAndReversedRangesAndUseExclusiveEnd() {
        val valid=TaxDraft(country="AL",vatNumber="VAT",effectiveFrom="2026-01-01T00:00:00Z",effectiveTo="2027-01-01T00:00:00Z")
        assertTrue(taxProblems(valid).isEmpty())
        assertTrue(taxProblems(valid.copy(effectiveTo=valid.effectiveFrom)).containsKey("effectiveTo"))
        assertTrue(taxProblems(valid.copy(effectiveFrom="not a date")).containsKey("effectiveFrom"))
        val tax=TaxProfileDto("t",effectiveFrom=valid.effectiveFrom,effectiveTo=valid.effectiveTo)
        assertTrue(tax.effectiveAt(taxInstant(valid.effectiveFrom)!!))
        assertFalse(tax.effectiveAt(taxInstant(valid.effectiveTo)!!))
    }
    @Test fun changingCurrencyClearsPreviewAndChoosesWholeUnitsForYen() {
        val model=details(session(all));model.startCurrencyChange()
        model.changeCurrencyFlow {it.copy(newCurrency="USD",rateText="2",preview=CurrencyPreviewDto("EUR","USD",previewToken="old"))}
        assertNull(model.state.value.currency?.preview)
        model.changeCurrencyFlow {it.copy(newCurrency="JPY")}
        assertEquals("1",model.state.value.currency?.step)
        model.changeCurrencyFlow {it.copy(newCurrency="EUR")}
        assertEquals("0.01",model.state.value.currency?.step)
    }
    @Test fun websiteValidationRejectsNonWebLinksAndLoginDetails() {
        for(url in listOf("https://example.com/menu", "http://example.com", "", "   "))assertNull(websiteProblem(url),url)
        for(url in listOf("javascript:alert(1)", "data:text/html,bad", "https://user:pass@example.com", "https://example .com", "example.com"))assertNotNull(websiteProblem(url),url)
    }
    @Test fun failedDetailSavePreservesEditsAndCancelDoesNotWrite() {
        val repo=object:PlatformRepository by repository {
            override suspend fun updateRestaurant(id:String,request:UpdateRestaurantRequestDto):RestaurantDto {throw ApiException(503,"Try later")}
        }
        val model=details(session(all),repo);model.editDetails();model.changeDetails {it.copy(phone="+355691112222")}
        model.saveDetails();scheduler.runCurrent();assertEquals("+355691112222",model.state.value.details?.phone)
        assertNotNull(model.state.value.error);assertFalse(model.state.value.saving)
        model.cancelDetails();assertNull(model.state.value.details);assertTrue(writes.isEmpty())
    }
    private fun render(scene:ImageComposeScene) {repeat(5){scheduler.runCurrent();scene.render(scheduler.currentTime*1_000_000L).close()}}
    private fun nodes(scene:ImageComposeScene):List<SemanticsNode> {
        fun all(n:SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap(::all)
        return scene.semanticsOwners.flatMap {all(it.unmergedRootSemanticsNode)}
    }
    private fun label(n:SemanticsNode):String=n.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString {it.text}+n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString()+n.children.joinToString {label(it)}
    private fun labels(scene:ImageComposeScene)=nodes(scene).joinToString {label(it)}
    private fun clickLabels(scene:ImageComposeScene)=nodes(scene).filter {it.config.getOrNull(SemanticsActions.OnClick)?.action!=null}.joinToString {label(it)}
    private fun click(scene:ImageComposeScene,text:String) {assertTrue(nodes(scene).last {label(it).contains(text)&&it.config.getOrNull(SemanticsActions.OnClick)?.action!=null}.config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke())}
    private inline fun <reified T> fake(crossinline call:(String,Array<out Any?>?)->Any?):T=Proxy.newProxyInstance(T::class.java.classLoader,arrayOf(T::class.java)) {_,m,args ->
        when(m.name){"toString"->"Test repository";"hashCode"->1;"equals"->false;else->call(m.name,args)}
    } as T
}
