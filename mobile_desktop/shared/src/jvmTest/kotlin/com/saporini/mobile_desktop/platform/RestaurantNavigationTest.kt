@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.saporini.mobile_desktop.platform

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import com.saporini.mobile_desktop.admin.people.PeopleRepository
import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.*
import com.saporini.mobile_desktop.core.theme.SaporiniTheme
import com.saporini.mobile_desktop.platform.ui.*
import com.saporini.mobile_desktop.workspace.ui.resolveStartScreen
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import java.lang.reflect.Proxy
import kotlin.test.*

class RestaurantNavigationTest {
    private val scheduler=TestCoroutineScheduler()
    private val dispatcher=StandardTestDispatcher(scheduler)
    private val disposals=mutableListOf<()->Unit>()
    private val permissions=listOf("RESTAURANTS_READ","RESTAURANTS_UPDATE","RESTAURANTS_CREATE","RESTAURANTS_DELETE", "POS_ACCESS","KDS_ACCESS","ADMIN_ACCESS","STATISTICS_ACCESS","FRAUD_ACCESS", "REPORTS_READ","FRAUD_READ","KDS_READ","ORDER_READ","STAFF_READ")
    private val user=CurrentUserResponse(id="u",email="test@example.invalid",username="tester",firstName="Test",lastName="User",isActive=true,emailVerified=true,phoneVerified=false,roles=listOf("SUPER_ADMIN"),permissions=permissions)
    private val session=SessionManager().apply {signIn(user)}
    private var restaurant=RestaurantDto("r","Restaurant",legalName="LLC",code="R",email="r@example.invalid",phone="+355691234567",currency="EUR",timezone="Europe/Tirane")
    private var branchCards=listOf(BranchCardDto("b","Main"))
    private var allCards=(1..130).map {RestaurantCardDto("r$it","Restaurant $it",branches=1,openBranches=1)}
    private val card=RestaurantCardDto("r","Restaurant",branches=1,openBranches=1)
    private var failCards=false
    private var failBranches=false
    private var failOrder=false
    private var failRestaurant=false
    private var savedOrder:List<String>?=null
    private val calls=mutableListOf<Pair<String,Int>>()
    private val branchCalls=mutableListOf<Pair<String,Int>>()
    // The server's paging and search of the branch cards: open ones first, totals for the whole restaurant.
    private fun cardPage(search:String,page:Int,size:Int):BranchCardPageDto {
        val matching=branchCards.filter {search.isBlank()||it.name.contains(search,true)||it.code.contains(search,true)}.sortedByDescending {it.open}
        return BranchCardPageDto(matching.drop(page*size).take(size),page,size,matching.size.toLong(),(matching.size+size-1)/size,(page+1)*size<matching.size,
            branchCards.size.toLong(),branchCards.count {it.open}.toLong())
    }
    private val people=fake<PeopleRepository> {_,_->error("Unexpected staff call")}
    private val repo=fake<PlatformRepository> {name,args-> when(name) {
        "cards" -> {
            val search=args!![0] as String;val filter=args[1] as String?;val page=args[2] as Int;val size=args[3] as Int
            calls+=search to page
            if(failCards)throw ApiException(503,"Offline")
            val matching=allCards.filter { (search.isBlank()||it.name.contains(search,true))&&(filter==null||it.open==(filter=="open")) }
            PageDto(matching.drop(page*size).take(size),page,size,matching.size.toLong(),(matching.size+size-1)/size,(page+1)*size<matching.size)
        }
        "restaurant" -> {if(failRestaurant)throw ApiException(404,"Removed");restaurant.copy(id=args!![0] as String)}
        "branchCards" -> {if(failBranches)throw ApiException(503,"Offline");branchCalls+=args!![1] as String to args[2] as Int;cardPage(args[1] as String,args[2] as Int,args[3] as Int)}
        "branch" -> {if(failBranches)throw ApiException(503,"Offline");branchCards.firstOrNull {it.id==args!![1]}?.let {BranchDto(it.id,it.name,status=it.status,isActive=it.active)}?:throw ApiException(404,"Branch not found")}
        "branches" -> branchCards.map {BranchDto(it.id,it.name,status=it.status,isActive=it.active)}
        "applications" -> PageDto<RestaurantApplicationDto>()
        "ownerTransfers" -> emptyList<Any>()
        "setRestaurantOrder" -> {if(failOrder)throw ApiException(503,"Offline");@Suppress("UNCHECKED_CAST") val ids=args!![0] as List<String>;savedOrder=ids;Unit}
        "branding" -> BrandingDto()
        "addresses","contacts","taxProfiles","currencyChanges" -> emptyList<Any>()
        else -> error("Unexpected $name")
    }}
    @BeforeTest fun before(){Dispatchers.setMain(dispatcher)}
    @AfterTest fun after(){disposals.forEach {it()};Dispatchers.resetMain()}
    private fun list(repository:PlatformRepository=repo)=RestaurantListScreenModel(repository,people,session).also {m->disposals+={m.onDispose()};scheduler.runCurrent();m.setActive(true);scheduler.runCurrent()}
    private fun branches()=BranchListScreenModel(repo,session).also {m->disposals+={m.onDispose()};scheduler.runCurrent();m.show("r","Restaurant");m.setActive(true);scheduler.runCurrent()}
    @Test fun largeListLoadsEveryPageAndRefreshPreservesLoadedPages(){
        val m=list();assertEquals(48,m.state.value.cards.size)
        m.loadMore();scheduler.runCurrent();m.loadMore();scheduler.runCurrent()
        assertEquals(130,m.state.value.cards.size);assertFalse(m.state.value.hasNext)
        m.refresh();scheduler.runCurrent();assertEquals(listOf(0,1,2),calls.takeLast(3).map {it.second});assertEquals(130,m.state.value.cards.size)
    }
    @Test fun searchAndFilterResetPagesAndClearOldResultsImmediately(){
        val m=list();m.loadMore();scheduler.runCurrent()
        m.search("Restaurant 13");assertTrue(m.state.value.cards.isEmpty());scheduler.runCurrent()
        assertEquals(listOf("r13","r130"),m.state.value.cards.map {it.id});assertEquals("Restaurant 13" to 0,calls.last())
        m.filter(RestaurantFilter.CLOSED);assertTrue(m.state.value.cards.isEmpty());scheduler.runCurrent();assertTrue(m.state.value.cards.isEmpty())
        m.search("x".repeat(150));scheduler.runCurrent();assertEquals(100,m.state.value.search.length)
    }
    @Test fun initialAndNextPageFailuresAreRetryableWithoutLosingLoadedRows(){
        failCards=true;val m=list();assertNotNull(m.state.value.error);assertFalse(m.state.value.loaded)
        failCards=false;m.refresh();scheduler.runCurrent();assertEquals(48,m.state.value.cards.size)
        failCards=true;m.loadMore();scheduler.runCurrent();assertEquals(48,m.state.value.cards.size);assertEquals(0,m.state.value.page)
        failCards=false;m.loadMore();scheduler.runCurrent();assertEquals(96,m.state.value.cards.size);assertNull(m.state.value.error)
    }
    @Test fun delayedSearchCannotRestoreOldResults(){
        val old=CompletableDeferred<PageDto<RestaurantCardDto>>()
        val repository=object:PlatformRepository by repo {override suspend fun cards(search:String,filter:String?,page:Int,size:Int):PageDto<RestaurantCardDto> {
            if(search.isEmpty())return withContext(NonCancellable){old.await()}
            return PageDto(listOf(card.copy(name="New")))
        }}
        val m=list(repository);m.search("New");scheduler.runCurrent();old.complete(PageDto(listOf(card)));scheduler.runCurrent()
        assertEquals("New",m.state.value.cards.single().name)
    }
    @Test fun reorderPreservesDraftAfterFailureAndCancelDoesNotWrite(){
        allCards=allCards.take(3);val m=list();m.startReorder();m.moveCards(allCards.reversed())
        m.search("ignored");m.filter(RestaurantFilter.CLOSED);m.refresh();scheduler.runCurrent()
        assertEquals("",m.state.value.search);assertEquals(RestaurantFilter.ALL,m.state.value.filter);assertEquals(allCards.reversed(),m.state.value.reorder)
        m.moveCards(listOf(allCards[0],allCards[0],allCards[1]));assertEquals(allCards.reversed(),m.state.value.reorder)
        failOrder=true;m.saveOrder();scheduler.runCurrent();assertNotNull(m.state.value.reorder);assertNotNull(m.state.value.error);assertFalse(m.state.value.savingOrder)
        failOrder=false;m.saveOrder();scheduler.runCurrent();assertEquals(allCards.reversed().map {it.id},savedOrder);assertNull(m.state.value.reorder)
        savedOrder=null;m.startReorder();m.moveCards(allCards);m.cancelReorder();assertNull(savedOrder)
    }
    @Test fun partialOrFilteredListsCannotBeReordered(){
        val m=list();m.startReorder();assertNull(m.state.value.reorder)
        m.search("Restaurant 1");scheduler.runCurrent();m.startReorder();assertNull(m.state.value.reorder)
    }
    @Test fun entryRechecksStaleRestaurantAndBranchCounts(){
        val m=list();branchCards=listOf(BranchCardDto("b","Main"),BranchCardDto("b2","Other"))
        var entered=0;var picked=0;m.enter(card,{entered++},{picked++});scheduler.runCurrent()
        assertEquals(0,entered);assertEquals(1,picked);assertNull(session.actingRestaurant.value)
        failRestaurant=true;m.enter(card,{entered++},{picked++});scheduler.runCurrent();assertNotNull(m.state.value.error);assertNull(m.state.value.entering);assertEquals(0,entered)
    }
    @Test fun closedRestaurantAndNoOpenBranchesOnlyExposeManagement(){
        val m=list();restaurant=restaurant.copy(status="SUSPENDED",isActive=false)
        m.enter(card,{},{});scheduler.runCurrent();assertNull(session.currentUser.value?.defaultBranchId)
        assertEquals(setOf(Workspace.RESTAURANT),accessibleWorkspaces(session.currentUser.value!!))
        session.leaveRestaurant();scheduler.runCurrent();restaurant=restaurant.copy(status="ACTIVE",isActive=true);branchCards=emptyList()
        m.enter(card,{},{});scheduler.runCurrent();assertEquals(setOf(Workspace.RESTAURANT),accessibleWorkspaces(session.currentUser.value!!))
    }
    @Test fun branchCardsRefuseClosedStaleAndDeletedSelections(){
        val m=branches();var entered=0
        m.choose(branchCards.single().copy(active=false)){entered++};scheduler.runCurrent();assertEquals(0,entered)
        val stale=branchCards.single();branchCards=emptyList();m.choose(stale){entered++};scheduler.runCurrent()
        assertEquals(0,entered);assertNull(session.actingRestaurant.value);assertNotNull(m.state.value.error)
        branchCards=listOf(stale);m.refresh();scheduler.runCurrent();m.choose(stale){entered++};scheduler.runCurrent()
        assertEquals(1,entered);assertEquals("b",session.currentUser.value?.defaultBranchId)
    }
    @Test fun branchFailureAndRestaurantSwitchClearStaleDrafts(){
        val m=branches();m.addBranch();m.editBranch {it.copy(name="Unsaved")};m.refresh();scheduler.runCurrent()
        assertEquals("Unsaved",m.state.value.branch?.draft?.name)
        failBranches=true;m.refresh();scheduler.runCurrent();assertEquals(1,m.state.value.cards.size);assertNotNull(m.state.value.error)
        m.show("other","Other");assertNull(m.state.value.branch);assertTrue(m.state.value.cards.isEmpty());scheduler.runCurrent()
        failBranches=false;m.refresh();scheduler.runCurrent();assertNull(m.state.value.error)
    }
    @Test fun editRefreshKeepsDraftAndClosingRequiresExplicitDiscard(){
        val m=list();m.startEdit(card);scheduler.runCurrent();m.changeEdit {it.copy(name="Unsaved")}
        m.refresh();scheduler.runCurrent();assertEquals("Unsaved",m.state.value.edit?.name)
        m.cancelEdit();assertNotNull(m.state.value.edit?.leaving);m.stayEditing();assertNotNull(m.state.value.edit)
        m.cancelEdit();m.discardAndLeave();assertNull(m.state.value.edit)
    }
    @Test fun restartNeverRestoresAnActingSelectionAndOldFailuresCannotClearANewOne(){
        session.enterRestaurant(ActingRestaurant("r","R","b","B"))
        val restarted=SessionManager().apply {signIn(user)};assertNull(restarted.actingRestaurant.value);assertEquals(RestaurantListScreen,resolveStartScreen(restarted.currentUser.value!!))
        session.enterRestaurant(ActingRestaurant("other","Other","b2","B2"))
        session.rejectRestaurantContext("r","b","Closed");assertEquals("other",session.currentUser.value?.restaurantId)
        session.rejectRestaurantContext("other","b2","Closed");assertNull(session.actingRestaurant.value);assertEquals("Closed",session.contextError.value)
    }
    @Test fun revokingRestaurantAccessClearsSelectionAndWorkspaceAccess(){
        session.enterRestaurant(ActingRestaurant("r","R","b","B"));session.signIn(user.copy(permissions=permissions-"RESTAURANTS_READ"))
        assertNull(session.actingRestaurant.value);assertTrue(accessibleWorkspaces(session.currentUser.value!!).isEmpty());assertNotNull(session.contextError.value)
    }
    @Test fun renderedRestaurantFailureRetriesAndEmptyResultsHaveAMessage(){
        failCards=true;val m=list()
        val scene=ImageComposeScene(1280,1000,coroutineContext=dispatcher){val s by m.state.collectAsState();TestPage {RestaurantListContent(s,m,false,{}, {}, {})}}
        try {render(scene);assertTrue(labels(scene).contains("Restaurants couldn't be loaded"));failCards=false;allCards=emptyList();click(scene,"Try again");render(scene);assertTrue(m.state.value.loaded);assertTrue(labels(scene).contains("Create a new restaurant"))}finally{scene.close()}
    }
    @Test fun renderedBranchFailureRetriesAndClosedBranchIsDisabled(){
        failBranches=true;val m=branches()
        val scene=ImageComposeScene(1280,900,coroutineContext=dispatcher){val s by m.state.collectAsState();TestPage {BranchListContent(s,m,false,{}, {})}}
        try {render(scene);assertTrue(labels(scene).contains("couldn't be loaded"));failBranches=false;branchCards=listOf(BranchCardDto("b","Closed branch",active=false));click(scene,"Try again");render(scene);assertTrue(labels(scene).contains("No open branches"));assertTrue(labels(scene).contains("Unavailable"));assertNull(session.actingRestaurant.value)}finally{scene.close()}
    }
    @Test fun everyWorkspaceModelTracksRestaurantAndBranchSwitches(){
        val orders=com.saporini.mobile_desktop.pos.orders.ui.OrdersScreenModel(fake {_,_->error("inactive")},session)
        val kds=com.saporini.mobile_desktop.kds.KdsScreenModel(fake {_,_->error("inactive")},fake {_,_->error("inactive")},session)
        val staff=com.saporini.mobile_desktop.admin.people.StaffScreenModel(people,session)
        val stats=com.saporini.mobile_desktop.statistics.StatisticsScreenModel(fake {_,_->error("inactive")},session)
        val fraud=com.saporini.mobile_desktop.fraud.FraudScreenModel(fake {_,_->error("inactive")},session)
        val details=RestaurantDetailsScreenModel(repo,people,session)
        disposals+=listOf(orders::onDispose,kds::onDispose,staff::onDispose,stats::onDispose,fraud::onDispose,details::onDispose)
        for((r,b) in listOf("r" to "b", "r" to "b2", "other" to "b3")) {
            session.enterRestaurant(ActingRestaurant(r,r,b,b));scheduler.runCurrent()
            assertEquals(r,orders.state.value.scope?.restaurantId);assertEquals(b,orders.state.value.scope?.branchId)
            assertEquals(r,kds.state.value.scope?.restaurantId);assertEquals(b,kds.state.value.scope?.branchId)
            assertEquals(r,staff.state.value.restaurantId);assertEquals(b,staff.state.value.myBranchId)
            assertEquals(r,stats.state.value.restaurantId);assertEquals(b,stats.state.value.branchId)
            assertEquals(r,fraud.state.value.restaurantId);assertEquals(b,fraud.state.value.branchId)
            assertEquals(r,details.state.value.restaurantId)
        }
        session.leaveRestaurant();scheduler.runCurrent()
        assertNull(orders.state.value.scope);assertNull(kds.state.value.scope);assertNull(staff.state.value.restaurantId)
        assertNull(stats.state.value.restaurantId);assertNull(fraud.state.value.restaurantId);assertNull(details.state.value.restaurantId)
    }
    @Test fun refreshingAnOpenEditKeepsItsOriginalVersionAndCannotRecreateARemovedBranch(){
        session.enterRestaurant(ActingRestaurant("r","R","b","B"))
        var sent:Long?=null
        val repository=object:PlatformRepository by repo {
            override suspend fun updateRestaurant(restaurantId:String,request:UpdateRestaurantRequestDto):RestaurantDto {
                sent=request.expectedVersion;throw ApiException(409,"Changed by another person")
            }
        }
        val m=RestaurantDetailsScreenModel(repository,people,session);disposals+={m.onDispose()};scheduler.runCurrent();m.setActive(true);scheduler.runCurrent()
        m.editDetails();m.changeDetails{it.copy(name="My change")};restaurant=restaurant.copy(version=9);m.refresh();scheduler.runCurrent();m.saveDetails();scheduler.runCurrent()
        assertEquals(0L,sent);assertNotNull(m.state.value.details);assertNotNull(m.state.value.error)
        m.cancelDetails();m.editBranch(m.state.value.branches.single());branchCards=emptyList();m.refresh();scheduler.runCurrent();m.saveBranch();scheduler.runCurrent()
        assertTrue(m.state.value.error.orEmpty().contains("removed"));assertNotNull(m.state.value.branch)
    }
    @Test fun renderedCardsOpenTheirRestaurantAndBranchAndNarrowEmptyStateFits(){
        allCards=listOf(card);val m=list();var entered=0
        val scene=ImageComposeScene(1280,1100,coroutineContext=dispatcher){val s by m.state.collectAsState();TestPage{RestaurantListContent(s,m,false,{entered++},{},{})}}
        try{render(scene);snapshot(scene,"restaurant-card");click(scene,"Open");render(scene);assertEquals(1,entered);assertEquals("b",session.currentUser.value?.defaultBranchId)}finally{scene.close()}
        session.leaveRestaurant();scheduler.runCurrent();val b=branches()
        val branchesScene=ImageComposeScene(420,900,coroutineContext=dispatcher){val s by b.state.collectAsState();TestPage{BranchListContent(s,b,true,{}, {entered++})}}
        try{render(branchesScene);snapshot(branchesScene,"branch-card-phone");click(branchesScene,"Choose");render(branchesScene);assertEquals(2,entered)}finally{branchesScene.close()}
        session.leaveRestaurant();scheduler.runCurrent();branchCards=emptyList();b.refresh();scheduler.runCurrent()
        val empty=ImageComposeScene(420,900,coroutineContext=dispatcher){TestPage{BranchListContent(b.state.value,b,true,{}, {})}}
        try{render(empty);assertTrue(labels(empty).contains("No branches yet"));snapshot(empty,"branches-empty-phone")}finally{empty.close()}
    }
    private fun snapshot(scene:ImageComposeScene,name:String){
        repeat(12){scheduler.advanceTimeBy(40);scheduler.runCurrent();scene.render(scheduler.currentTime*1_000_000L).close()}
        val folder=java.io.File("build/reports/restaurant-navigation").apply{mkdirs()}
        scene.render(scheduler.currentTime*1_000_000L).use {image->image.encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.use {java.io.File(folder,"$name.png").writeBytes(it.bytes)}}
    }
    @Test fun leavingWhileEntryLoadsCannotNavigateBackIntoTheRestaurant(){
        val gate=CompletableDeferred<RestaurantDto>()
        val delayed=object:PlatformRepository by repo {override suspend fun restaurant(restaurantId:String)=withContext(NonCancellable){gate.await()}}
        val m=list(delayed);var entered=0;m.enter(card,{entered++},{});scheduler.runCurrent();assertNotNull(m.state.value.entering)
        m.setActive(false);gate.complete(restaurant);scheduler.runCurrent();assertNull(session.actingRestaurant.value);assertEquals(0,entered);assertNull(m.state.value.entering)
    }
    @Test fun appNavigatorSurvivesContextChangesAndReturnsToTheListOnRejection(){
        assertTrue(System.getProperty("user.home").endsWith("test-home"));TokenStore.clear();allCards=listOf(card)
        val client=io.ktor.client.HttpClient(io.ktor.client.engine.mock.MockEngine{error("Unexpected HTTP request")})
        val auth=com.saporini.mobile_desktop.auth.data.repository.AuthRepository(client)
        org.koin.core.context.startKoin {modules(org.koin.dsl.module {
            single {session};single {auth};single<PlatformRepository> {repo}
            factory {RestaurantListScreenModel(repo,people,session)}
            factory {com.saporini.mobile_desktop.workspace.ui.WorkspaceScreenModel(auth,session)}
            factory {com.saporini.mobile_desktop.auth.ui.login.LoginScreenModel(auth,session)}
        })}
        val owner=object:androidx.lifecycle.LifecycleOwner {
            override val lifecycle=androidx.lifecycle.LifecycleRegistry.createUnsafe(this).apply{currentState=androidx.lifecycle.Lifecycle.State.RESUMED}
        }
        val scene=ImageComposeScene(1280,1100,coroutineContext=dispatcher){
            androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner){com.saporini.mobile_desktop.App()}
        }
        try{
            render(scene);click(scene,"Open");render(scene)
            assertTrue(labels(scene).contains("Choose your workspace"));assertEquals("b",session.actingRestaurant.value?.branchId)
            snapshot(scene,"workspace-picker")
            branchCards=listOf(BranchCardDto("b","Main"),BranchCardDto("b2","Other branch"),BranchCardDto("closed","Closed branch",active=false))
            failBranches=true;click(scene,"Switch branch");render(scene)
            assertTrue(labels(scene).contains("Try again"));failBranches=false;click(scene,"Try again");render(scene)
            assertTrue(nodes(scene).any{label(it).contains("Closed branch")&&it.config.contains(SemanticsProperties.Disabled)})
            click(scene,"Other branch");render(scene);assertEquals("b2",session.actingRestaurant.value?.branchId)
            assertTrue(labels(scene).contains("Choose your workspace"))
            session.rejectRestaurantContext("r","b2","Branch closed");render(scene)
            assertNull(session.actingRestaurant.value);assertFalse(labels(scene).contains("Choose your workspace"));assertTrue(labels(scene).contains("Restaurants"))
            session.signOut();render(scene);session.signIn(user);render(scene)
            assertTrue(labels(scene).contains("Restaurants"))
        }finally{scene.close();org.koin.core.context.stopKoin();client.close();TokenStore.clear()}
    }
    @Test fun newSearchCancelsAnEarlierRestaurantEntry(){
        val gate=CompletableDeferred<RestaurantDto>()
        val delayed=object:PlatformRepository by repo{override suspend fun restaurant(restaurantId:String)=gate.await()}
        val m=list(delayed);var entered=0;m.enter(card,{entered++},{});scheduler.runCurrent()
        m.search("New");gate.complete(restaurant);scheduler.runCurrent()
        assertEquals(0,entered);assertNull(m.state.value.entering);assertNull(session.actingRestaurant.value)
    }
    @Test fun staffWithoutAnOpenBranchCanRetryAfterReassignmentOrSignOut(){
        TokenStore.clear()
        val staff=user.copy(roles=listOf("WAITER"),restaurantId="r",defaultBranchId=null,permissions=listOf("POS_ACCESS","KDS_ACCESS"))
        session.signIn(staff)
        assertTrue(accessibleWorkspaces(staff).isEmpty())
        assertEquals(setOf(Workspace.ADMIN,Workspace.STATISTICS),accessibleWorkspaces(staff.copy(permissions=staff.permissions+listOf("ADMIN_ACCESS","STATISTICS_ACCESS"))))
        assertEquals(com.saporini.mobile_desktop.workspace.ui.NoAccessScreen,resolveStartScreen(staff))
        var fail=true
        session.setUserLoader {if(fail)throw ApiException(503,"Offline");staff.copy(defaultBranchId="b2")}
        val client=io.ktor.client.HttpClient(io.ktor.client.engine.mock.MockEngine{error("Unexpected HTTP")})
        val auth=com.saporini.mobile_desktop.auth.data.repository.AuthRepository(client)
        org.koin.core.context.startKoin {modules(org.koin.dsl.module {
            single {session}
            factory {com.saporini.mobile_desktop.workspace.ui.WorkspaceScreenModel(auth,session)}
        })}
        val scene=ImageComposeScene(420,800,coroutineContext=dispatcher){TestPage {
            cafe.adriel.voyager.navigator.Navigator(com.saporini.mobile_desktop.workspace.ui.NoAccessScreen){cafe.adriel.voyager.navigator.CurrentScreen()}
        }}
        try{
            render(scene);assertTrue(labels(scene).contains("No open branch assigned"));assertTrue(labels(scene).contains("Sign out"))
            click(scene,"Try again");render(scene);assertNull(session.currentUser.value?.defaultBranchId);assertTrue(session.isAuthenticated.value)
            fail=false;click(scene,"Try again");render(scene);assertEquals("b2",session.currentUser.value?.defaultBranchId);assertTrue(labels(scene).contains("Choose your workspace"))
            snapshot(scene,"staff-reassigned")
        }finally{scene.close();org.koin.core.context.stopKoin();client.close();TokenStore.clear()}
    }
    @Test fun branchRemovalRequiresChoiceNameAndFreshPreviewAndKeepsDraftOnFailure(){
        session.enterRestaurant(ActingRestaurant("r","R","b","Main"))
        var failure=true
        var sent:BranchRemovalRequestDto?=null
        var plan=BranchRemovalPlanDto(canRemove=true,staff=3,protectedStaff=1,devices=1,tables=2,kitchenStations=1,stockLocations=1,canDeactivateStaff=true,expectedVersion=4,previewToken="preview")
        val repository=object:PlatformRepository by repo {
            override suspend fun branchRemovalCheck(restaurantId:String,branchId:String)=BranchRemovalCheckDto(archive=plan)
            override suspend fun removeBranch(restaurantId:String,branchId:String,request:BranchRemovalRequestDto){
                if(failure)throw ApiException(409,"Staff changed. Review again.")
                sent=request;branchCards=emptyList()
            }
        }
        val m=RestaurantDetailsScreenModel(repository,people,session);disposals+={m.onDispose()};scheduler.runCurrent();m.setActive(true);scheduler.runCurrent()
        val b=m.state.value.branches.single();m.askRemoveBranch(b);scheduler.runCurrent();m.confirm();scheduler.runCurrent();assertNull(sent)
        val scene=ImageComposeScene(780,1100,coroutineContext=dispatcher){val st by m.state.collectAsState();TestPage {RestaurantDetailsContent(st,m,st.tab,m::tab,true)}}
        try {
            render(scene);assertTrue(labels(scene).contains("keep staff for reassignment"));assertTrue(labels(scene).contains("deactivate its staff"));assertTrue(labels(scene).contains("historical records stay"))
            click(scene,"Remove branch and deactivate its staff");render(scene)
            val input=nodes(scene).last {it.config.getOrNull(SemanticsActions.SetText)?.action!=null}
            input.config.getOrNull(SemanticsActions.SetText)!!.action!!.invoke(androidx.compose.ui.text.AnnotatedString("Main"));render(scene)
            snapshot(scene,"branch-removal-choices")
            val phone=ImageComposeScene(420,850,coroutineContext=dispatcher){val st by m.state.collectAsState();TestPage {RestaurantDetailsContent(st,m,st.tab,m::tab,true)}}
            try {render(phone);snapshot(phone,"branch-removal-phone")}finally{phone.close()}
            m.confirm();scheduler.runCurrent();assertNull(sent);assertEquals("DEACTIVATE",(m.state.value.confirm as RestaurantConfirm.RemoveBranch).staffAction);assertNotNull(m.state.value.error)
            plan=plan.copy(canDeactivateStaff=false,previewToken="fresh");render(scene);click(scene,"Review again");render(scene)
            assertNull((m.state.value.confirm as RestaurantConfirm.RemoveBranch).staffAction)
            assertFalse(labels(scene).contains("Remove branch and deactivate its staff"))
            m.changeBranchRemoval(staffAction="DEACTIVATE",confirmedName="Main");assertNull((m.state.value.confirm as RestaurantConfirm.RemoveBranch).staffAction)
            failure=false;click(scene,"Remove branch, keep staff for reassignment");m.changeBranchRemoval(confirmedName="Main");m.confirm();scheduler.runCurrent()
            assertEquals(BranchRemovalRequestDto("KEEP",4,"Main","fresh"),sent);assertNull(m.state.value.confirm)
            assertNull(session.actingRestaurant.value?.branchId);assertEquals(setOf(Workspace.RESTAURANT),accessibleWorkspaces(session.currentUser.value!!))
        }finally{scene.close()}
    }
    @androidx.compose.runtime.Composable
    private fun TestPage(content:@androidx.compose.runtime.Composable ()->Unit){
        SaporiniTheme {androidx.compose.material3.Surface(modifier=androidx.compose.ui.Modifier.fillMaxSize(),color=androidx.compose.ui.graphics.Color(0xFFFCFCFB)){content()}}
    }
    private fun render(s:ImageComposeScene){repeat(6){scheduler.runCurrent();s.render(scheduler.currentTime*1_000_000L).close()}}
    private fun nodes(s:ImageComposeScene):List<SemanticsNode>{
        fun all(n:SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap(::all)
        return s.semanticsOwners.flatMap {all(it.unmergedRootSemanticsNode)}
    }
    private fun label(n:SemanticsNode):String=n.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString {it.text}+n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString()+n.children.joinToString {label(it)}
    private fun labels(s:ImageComposeScene)=nodes(s).joinToString {label(it)}
    private fun click(s:ImageComposeScene,t:String){assertTrue(nodes(s).last {label(it).contains(t)&&it.config.getOrNull(SemanticsActions.OnClick)?.action!=null}.config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke())}
    private inline fun <reified T> fake(crossinline call:(String,Array<out Any?>?)->Any?):T=Proxy.newProxyInstance(T::class.java.classLoader,arrayOf(T::class.java)){_,m,a->when(m.name){"toString"->"Test repository";"hashCode"->1;"equals"->false;else->call(m.name,a)}} as T
}
