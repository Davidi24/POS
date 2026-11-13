package com.saporini.mobile_desktop.platform.ui

import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.saporini.mobile_desktop.auth.ui.login.LoginScreen
import com.saporini.mobile_desktop.core.components.BindToLifecycle
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.ScreenMessages
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.RetryText
import com.saporini.mobile_desktop.core.components.SearchField
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.platform.BranchCardDto
import com.saporini.mobile_desktop.platform.BranchListScreenModel
import com.saporini.mobile_desktop.platform.BranchListState
import com.saporini.mobile_desktop.platform.branchStatusLabel
import com.saporini.mobile_desktop.workspace.ui.PageDecorations
import com.saporini.mobile_desktop.workspace.ui.PickerTopBar
import com.saporini.mobile_desktop.workspace.ui.WorkspacePickerScreen
import com.saporini.mobile_desktop.workspace.ui.WorkspaceScreenModel
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

/** "Choose a branch": opened from the restaurant list for a restaurant with several branches; choosing one opens the six apps. */
data class BranchListScreen(val restaurantId: String, val restaurantName: String) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val session = koinInject<SessionManager>()
        val model = koinInject<BranchListScreenModel>()
        BindToLifecycle(model::setActive, model::onDispose)
        LaunchedEffect(restaurantId) { model.show(restaurantId, restaurantName) }
        val state by model.state.collectAsState()
        val user by session.currentUser.collectAsState()
        val authenticated by session.isAuthenticated.collectAsState()
        val workspaceModel = koinInject<WorkspaceScreenModel>()
        val workspaceState by workspaceModel.state.collectAsState()
        LaunchedEffect(authenticated, workspaceState.loggedOut) { if (!authenticated || workspaceState.loggedOut) navigator.replaceAll(LoginScreen) }
        // Back here from the six apps: no branch is entered until one is chosen again.
        LaunchedEffect(Unit) { session.leaveRestaurant() }

        BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFFFCFCFB))) {
            val narrow = maxWidth < 760.dp
            PageDecorations(Modifier.fillMaxSize(), narrow)
            val hideBar = com.saporini.mobile_desktop.core.components.rememberHideOnScroll(narrow)
            Column(Modifier.fillMaxSize()) {
                com.saporini.mobile_desktop.core.components.HideOnScrollBar(hideBar) {
                    PickerTopBar(user, workspaceState.isLoggingOut, onLogout = workspaceModel::logout, compact = narrow)
                }
                BranchListContent(
                    state, model, narrow,
                    onBack = { if (!navigator.pop()) navigator.replaceAll(RestaurantListScreen) },
                    onEntered = { navigator.push(WorkspacePickerScreen) },
                    modifier = Modifier.weight(1f).fillMaxWidth().nestedScroll(hideBar.connection)
                )
            }
        }
    }
}

@Composable
internal fun BranchListContent(
    state: BranchListState,
    model: BranchListScreenModel,
    narrow: Boolean,
    onBack: () -> Unit,
    onEntered: () -> Unit,
    modifier: Modifier = Modifier
) {
    val side = if (narrow) 16.dp else 36.dp
    ScreenMessages(state.error.takeIf { state.loaded }, state.notice, model::clearMessages)
    var query by remember(state.search) { mutableStateOf(state.search) }
    LaunchedEffect(query) {
        delay(300)
        model.search(query.trim())
    }
    val grid = rememberLazyGridState()
    LaunchedEffect(state.search) { grid.scrollToItem(0) }
    val nearEnd by remember(grid) {
        derivedStateOf {
            val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= grid.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, state.hasNext, state.loading, state.loadingMore, state.cards.size) {
        if (nearEnd && state.error == null) model.loadMore()
    }
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            GridCells.Adaptive(if (narrow) 280.dp else 340.dp),
            Modifier.widthIn(max = 1280.dp).fillMaxSize(),
            state = grid,
            contentPadding = PaddingValues(start = side, end = side, top = if (narrow) 4.dp else 10.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BackArrow("Back to the restaurants", onBack)
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text("Choose a branch", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (narrow) 18.sp else 21.sp, color = Kit.Ink)
                        Text(
                            if (state.loaded) "${state.restaurantName} · ${branchCount(state.allBranches)} · ${state.openBranches} open" else state.restaurantName,
                            fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Kit.Muted
                        )
                    }
                    com.saporini.mobile_desktop.core.components.RefreshButton(state.loading, model::refresh)
                }
            }
            // Searching makes sense once there is more than a handful; it stays while something is searched.
            if (state.canRead && (state.allBranches > 6 || state.search.isNotBlank())) {
                item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SearchField(query, { query = it.take(100) }, if (narrow) Modifier.weight(1f) else Modifier.width(380.dp),
                            placeholder = "Search by name, code, city or street", height = 46.dp)
                        if (state.loaded && state.search.isNotBlank()) {
                            Text(if (state.total == 1L) "1 branch found" else "${state.total} branches found", fontFamily = Inter(),
                                fontSize = 13.sp, color = Kit.Muted)
                        }
                    }
                }
            }
            when {
                !state.canRead -> item(key = "denied", span = { GridItemSpan(maxLineSpan) }) {
                    OverviewEmpty("Access unavailable", "You do not have permission to view branches.", Icons.Outlined.Business)
                }
                !state.loaded && state.loading -> item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Kit.Green) }
                }
                !state.loaded && state.error != null -> item(key = "failed", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        OverviewEmpty("The branches couldn't be loaded", "Check the connection and try again.", Icons.Outlined.Business,
                            action = { RetryText(onClick = model::refresh) })
                    }
                }
                else -> {
                    if (state.cards.isEmpty() && state.search.isNotBlank()) item(key = "nomatch", span = { GridItemSpan(maxLineSpan) }) {
                        OverviewEmpty("No branch matches", "Try another name, code, city or street.", Icons.Outlined.Search,
                            action = { RetryText("Clear the search") { query = "" } })
                    } else if (state.cards.isEmpty()) item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        OverviewEmpty("No branches yet", "Add a branch to start working in this restaurant.", Icons.Outlined.Business)
                    } else if (!state.restaurantOpen || state.openBranches == 0L) item(key = "unavailable", span = { GridItemSpan(maxLineSpan) }) {
                        OverviewEmpty("No open branches", if (state.restaurantOpen) "The branches below are closed or inactive." else "This restaurant is closed or suspended.", Icons.Outlined.Business)
                    }
                    items(state.cards, key = { it.id }) { card -> BranchCard(card, state, model) { model.choose(card, onEntered) } }
                    if (state.hasNext || state.loadingMore) item(key = "more", span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            if (state.loadingMore) CircularProgressIndicator(Modifier.size(22.dp), color = Kit.Green, strokeWidth = 2.dp)
                            else if (state.error != null) RetryText(onClick = model::loadMore)
                            else com.saporini.mobile_desktop.core.components.KitButton("Show more branches", model::loadMore)
                        }
                    }
                    if (state.canChangeCover && !state.hasNext && state.search.isBlank()) item(key = "add", span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            AddTile("Add a branch", "It opens in the POS, kitchen and bookings", model::addBranch,
                                modifier = Modifier.width(300.dp))
                        }
                    }
                }
            }
        }
    }
    state.branch?.let { step ->
        BranchFormDialog("Add a branch to ${step.restaurantName}", step.draft, step.saving, step.error, model::editBranch, model::saveBranch,
            model::cancelBranch, saveText = "Add branch")
    }
}

private fun branchCount(count: Long) = if (count == 1L) "1 branch" else "$count branches"

@Composable
private fun BranchCard(card: BranchCardDto, state: BranchListState, model: BranchListScreenModel, onChoose: () -> Unit) {
    PlaceCard(
        accentKey = card.id,
        title = card.name,
        place = listOfNotNull(card.street, card.city).joinToString(", ").ifBlank { "No address yet" },
        coverUrl = card.coverUrl,
        logoUrl = null,
        status = branchStatusLabel(card.status, card.active),
        statusColor = if (card.open) Kit.Green else Kit.Grey,
        stats = listOf(
            "${card.staff}" to "staff",
            "${card.devices}" to if (card.devices == 1L) "device" else "devices",
            card.code to "code"
        ),
        actionText = if (card.open && state.restaurantOpen) "Choose" else "Unavailable",
        entering = state.entering == card.id,
        highlighted = false,
        enabled = card.open && state.restaurantOpen && state.entering == null,
        canChangeCover = state.canChangeCover,
        coverBusy = card.id in state.coverBusy,
        onCover = { picture -> model.uploadCover(card, picture.bytes, picture.fileName, picture.contentType) },
        onRemoveCover = { model.removeCover(card) },
        onCoverProblem = model::coverProblem,
        onOpen = onChoose
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            FooterLine(Icons.Outlined.Phone, card.phone ?: "No phone yet")
            FooterLine(Icons.Outlined.Mail, card.email ?: "No email yet")
        }
    }
}

