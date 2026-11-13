package com.saporini.mobile_desktop.platform.ui

import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DragIndicator
import com.saporini.mobile_desktop.core.components.ScreenMessages
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.rememberCoroutineScope
import com.saporini.mobile_desktop.pos.menu.ui.item.MaxPhotoBytes
import com.saporini.mobile_desktop.pos.menu.ui.item.photoContentType
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.decodeToImageBitmap
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import com.saporini.mobile_desktop.core.components.KitDivider
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.saporini.mobile_desktop.auth.ui.login.LoginScreen
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.BindToLifecycle
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.FieldPair
import com.saporini.mobile_desktop.core.components.FilterPills
import com.saporini.mobile_desktop.core.components.GroupLabel
import com.saporini.mobile_desktop.core.components.InitialsAvatar
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.OverviewTabs
import com.saporini.mobile_desktop.core.components.RetryText
import com.saporini.mobile_desktop.core.components.SearchableSelect
import com.saporini.mobile_desktop.core.components.StatusChip
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.RemoteImage
import com.saporini.mobile_desktop.platform.CURRENCIES
import com.saporini.mobile_desktop.platform.MAX_BRANCH_NAME
import com.saporini.mobile_desktop.platform.MAX_EMAIL_TEXT
import com.saporini.mobile_desktop.platform.MAX_LEGAL_NAME
import com.saporini.mobile_desktop.platform.MAX_RESTAURANT_NAME
import com.saporini.mobile_desktop.platform.NewRestaurantDraft
import com.saporini.mobile_desktop.platform.RestaurantCardDto
import com.saporini.mobile_desktop.platform.RestaurantFilter
import com.saporini.mobile_desktop.platform.RestaurantListScreenModel
import com.saporini.mobile_desktop.platform.RestaurantListState
import com.saporini.mobile_desktop.platform.TIME_ZONES
import com.saporini.mobile_desktop.platform.branchStatusLabel
import com.saporini.mobile_desktop.platform.newRestaurantProblems
import com.saporini.mobile_desktop.platform.restaurantStatusLabel
import com.saporini.mobile_desktop.platform.timeZoneLabel
import com.saporini.mobile_desktop.pos.menu.ui.justCreated
import com.saporini.mobile_desktop.workspace.ui.PageDecorations
import com.saporini.mobile_desktop.workspace.ui.PickerTopBar
import com.saporini.mobile_desktop.workspace.ui.WorkspacePickerScreen
import com.saporini.mobile_desktop.core.components.SearchField
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.workspace_restaurants
import org.koin.compose.koinInject

private val PageBackground = Color(0xFFFCFCFB)

/**
 * The Super Admin's and Co-Super-Admins' home after signing in: every restaurant, "Create a new restaurant", and
 * the Co-Super-Admins (Super Admin only). Opening a restaurant (and choosing a branch when it has several) leads to
 * its workspaces; a closed or branch-less one ("Manage") opens its own page, since there's nothing to work in yet.
 */
object RestaurantListScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val session = koinInject<SessionManager>()
        val model = koinInject<RestaurantListScreenModel>()
        BindToLifecycle(model::setActive, model::onDispose)
        val state by model.state.collectAsState()
        val user by session.currentUser.collectAsState()
        val authenticated by session.isAuthenticated.collectAsState()
        // Logging out also ends the session on the server.
        val workspaceModel = koinInject<com.saporini.mobile_desktop.workspace.ui.WorkspaceScreenModel>()
        val workspaceState by workspaceModel.state.collectAsState()
        LaunchedEffect(authenticated, workspaceState.loggedOut) { if (!authenticated || workspaceState.loggedOut) navigator.replaceAll(LoginScreen) }
        // Coming back from a restaurant leaves it, so the next one starts clean.
        LaunchedEffect(Unit) { session.leaveRestaurant() }

        BoxWithConstraints(Modifier.fillMaxSize().background(PageBackground)) {
            val narrow = maxWidth < 760.dp
            PageDecorations(Modifier.fillMaxSize(), narrow)
            // A phone: the bar slides away while the list scrolls down and comes back as it scrolls up.
            val hideBar = com.saporini.mobile_desktop.core.components.rememberHideOnScroll(narrow)
            Column(Modifier.fillMaxSize()) {
                com.saporini.mobile_desktop.core.components.HideOnScrollBar(hideBar) {
                    PickerTopBar(user, workspaceState.isLoggingOut, onLogout = { if (state.reorder != null) model.coverProblem("Save or cancel the restaurant order before leaving.") else workspaceModel.logout() }, compact = narrow)
                }
                RestaurantListContent(
                    state, model, narrow,
                    // Entered without a branch (closed, or no open branch yet): straight to the restaurant's page, which the
                    // picker doesn't offer (user rule).
                    onEntered = { navigator.push(if (session.actingRestaurant.value?.branchId == null) RestaurantWorkspaceScreen else WorkspacePickerScreen) },
                    onChooseBranch = { card -> navigator.push(BranchListScreen(card.id, card.name)) },
                    onCoSuperAdmins = { if (state.reorder != null) model.coverProblem("Save or cancel the restaurant order before leaving.") else navigator.push(CoSuperAdminsScreen) },
                    onApplications = { if (state.reorder != null) model.coverProblem("Save or cancel the restaurant order before leaving.") else navigator.push(RestaurantApplicationsScreen) },
                    modifier = Modifier.weight(1f).fillMaxWidth().nestedScroll(hideBar.connection)
                )
            }
        }
    }
}

@Composable
internal fun RestaurantListContent(
    state: RestaurantListState,
    model: RestaurantListScreenModel,
    narrow: Boolean,
    onEntered: () -> Unit,
    onChooseBranch: (RestaurantCardDto) -> Unit,
    onCoSuperAdmins: () -> Unit,
    onApplications: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var query by remember(state.search) { mutableStateOf(state.search) }
    LaunchedEffect(query) {
        delay(300)
        model.search(query.trim())
    }
    val grid = rememberLazyGridState()
    LaunchedEffect(state.search, state.filter) { grid.scrollToItem(0) }
    val nearEnd by remember(grid) {
        derivedStateOf {
            val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= grid.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, state.hasNext, state.loading, state.loadingMore, state.cards.size) {
        if (nearEnd && state.error == null) model.loadMore()
    }
    // After setting up a restaurant: scroll to its card (the welcome banner and toolbar come first) and mark it for a moment.
    LaunchedEffect(state.justCreated, state.cards) {
        val id = state.justCreated ?: return@LaunchedEffect
        val index = state.cards.indexOfFirst { it.id == id }
        if (index >= 0) grid.animateScrollToItem(index + 2)
    }
    LaunchedEffect(state.justCreated) {
        if (state.justCreated != null) {
            delay(3_500)
            model.clearJustCreated()
        }
    }
    val side = if (narrow) 16.dp else 36.dp
    val full: (androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan) = { GridItemSpan(maxLineSpan) }
    ScreenMessages(state.error.takeIf { state.draft == null && state.loaded }, state.notice, model::clearMessages)
    val reorder = state.reorder
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            GridCells.Adaptive(if (narrow) 280.dp else 340.dp),
            Modifier.widthIn(max = 1280.dp).fillMaxSize(),
            state = grid,
            contentPadding = PaddingValues(start = side, end = side, top = if (narrow) 4.dp else 10.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item(key = "hero", span = full) { WelcomeBanner(state, model, narrow, onCoSuperAdmins, onApplications) }
            item(key = "toolbar", span = full) { ListToolbar(query, { if (state.reorder == null) query = it.take(100) }, state, model, narrow) }
            when {
                !state.canRead -> item(key = "denied", span = full) {
                    OverviewEmpty("Access unavailable", "You do not have permission to view restaurants.", Icons.Outlined.Storefront)
                }
                !state.loaded && state.loading -> item(key = "loading", span = full) {
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Kit.Green) }
                }
                !state.loaded && state.error != null -> item(key = "failed", span = full) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        OverviewEmpty("Restaurants couldn't be loaded", "Check the connection and try again.", Icons.Outlined.Storefront,
                            action = { RetryText(onClick = model::refresh) })
                    }
                }
                state.cards.isEmpty() && (state.search.isNotBlank() || state.filter != RestaurantFilter.ALL) -> item(key = "nomatch", span = full) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        OverviewEmpty("No restaurant matches", "Try another name, code or city, or show all restaurants.", Icons.Outlined.Search)
                    }
                }
                state.cards.isEmpty() -> item(key = "empty", span = full) { FirstRestaurant(state.canCreate, model::startCreate) }
                // "Change position": the cards as smaller tiles that can be dragged.
                reorder != null -> item(key = "reorder", span = full) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (narrow) "Hold a restaurant, then drag it to its new place." else "Drag a restaurant to its new place, then save the order.",
                            fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted)
                        ReorderGrid(reorder, { it.id }, model::moveCards, minTileWidth = if (narrow) 280.dp else 340.dp, tileHeight = 196.dp,
                            touch = narrow) { card, dragging -> ReorderTile(card, dragging) }
                    }
                }
                else -> {
                    items(state.cards, key = { it.id }) { card ->
                        RestaurantCard(card, state, model) { model.enter(card, onEntered, onChooseBranch) }
                    }
                    if (state.canCreate && !state.hasNext && state.search.isBlank()) {
                        // A small tile on its own line, in the middle.
                        item(key = "add", span = full) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                AddTile("Create a new restaurant", "Its owner gets an email to set a password", model::startCreate,
                                    modifier = Modifier.width(300.dp))
                            }
                        }
                    }
                }
            }
            if (state.hasNext || state.loadingMore) {
                item(key = "more", span = full) {
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        if (state.loadingMore) CircularProgressIndicator(Modifier.size(22.dp), color = Kit.Green, strokeWidth = 2.dp)
                        else if (state.error != null) RetryText(onClick = model::loadMore)
                        else KitButton("Load more", model::loadMore)
                    }
                }
            }
        }
        // Bottom-right, like the menu items' "Change items position".
        if (state.canManage && state.loaded && state.cards.size >= 2) {
            Row(Modifier.align(Alignment.BottomEnd).padding(if (narrow) 12.dp else 22.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (reorder == null) {
                    FloatingAction("Change position", Icons.Outlined.DragIndicator, Kit.Green, enabled = state.entering == null, onClick = model::startReorder)
                } else {
                    FloatingAction("Cancel", Icons.Outlined.Close, Color.White, contentColor = Kit.Ink, enabled = !state.savingOrder, onClick = model::cancelReorder)
                    FloatingAction("Save order", Icons.Outlined.Check, Kit.Ink, busy = state.savingOrder, onClick = model::saveOrder)
                }
            }
        }
    }

    if (state.draft != null || state.created != null || state.owner?.afterCreate == true) {
        SetupDialog(state, model)
    } else {
        state.owner?.let { OwnerDialog(it, model::editOwner, model::saveOwner, model::skipOwner) }
    }
    state.edit?.let { EditRestaurantDialog(it, model) }
    state.branch?.let { step ->
        BranchFormDialog("Add a branch to ${step.restaurantName}", step.draft, step.saving, step.error, model::editBranch, model::saveBranch,
            model::cancelBranch, saveText = "Add branch")
    }
}

@Composable
private fun FloatingAction(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    contentColor: Color = Color.White,
    enabled: Boolean = true,
    busy: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        Modifier.height(44.dp).shadow(12.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).background(color)
            .border(1.dp, if (color == Color.White) Kit.Border else color, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled && !busy, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = contentColor, strokeWidth = 2.dp)
        else Icon(icon, null, Modifier.size(18.dp), tint = contentColor)
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = contentColor)
    }
}

/** A restaurant while its place is being changed: smaller, with a handle to drag. */
@Composable
private fun ReorderTile(card: RestaurantCardDto, dragging: Boolean) {
    val accent = accentOf(card.id)
    val shape = RoundedCornerShape(18.dp)
    Surface(
        Modifier.fillMaxSize(), shape = shape, color = Color.White,
        border = BorderStroke(if (dragging) 1.5.dp else 1.dp, if (dragging) accent.strong else Kit.Border),
        shadowElevation = if (dragging) 18.dp else 2.dp
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(100.dp)) { CoverBackground(card.coverUrl, accent) }
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LogoTile(card.logoUrl, card.name, accent, Modifier.size(48.dp).clip(RoundedCornerShape(13.dp)), 17)
                Column(Modifier.weight(1f)) {
                    Text(card.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(card.city, card.country).joinToString(", ").ifBlank { "No address yet" }, fontFamily = Inter(), fontSize = 12.sp,
                        color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Outlined.DragIndicator, "Drag to move", Modifier.size(22.dp), tint = if (dragging) accent.strong else Kit.Muted)
            }
        }
    }
}

// ---- Welcome banner and toolbar ----

@Composable
private fun WelcomeBanner(
    state: RestaurantListState,
    model: RestaurantListScreenModel,
    narrow: Boolean,
    onCoSuperAdmins: () -> Unit,
    onApplications: () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Surface(Modifier.fillMaxWidth(), shape = shape, color = Color.White, border = BorderStroke(1.dp, Kit.Border), shadowElevation = 3.dp) {
        Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color.White, Color(0xFFF3F7F0), Color(0xFFE6EFE1))))) {
            // Soft rings behind the picture.
            Canvas(Modifier.matchParentSize()) {
                val center = Offset(size.width - 150.dp.toPx(), size.height * 0.55f)
                drawCircle(Kit.Green.copy(alpha = 0.06f), radius = 170.dp.toPx(), center = center)
                drawCircle(Kit.Green.copy(alpha = 0.07f), radius = 110.dp.toPx(), center = center)
            }
            Row(Modifier.padding(horizontal = if (narrow) 20.dp else 32.dp, vertical = if (narrow) 20.dp else 26.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(Kit.Green).padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Outlined.AdminPanelSettings, null, Modifier.size(13.dp), tint = Color.White)
                        Text(if (state.superAdmin) "SUPER ADMIN" else "CO-SUPER-ADMIN", fontFamily = Inter(), fontWeight = FontWeight.Bold,
                            fontSize = 10.sp, letterSpacing = 0.8.sp, color = Color.White)
                    }
                    Text(if (state.firstName.isBlank()) "Welcome back" else "Welcome back, ${state.firstName}", fontFamily = Inter(),
                        fontWeight = FontWeight.Bold, fontSize = if (narrow) 18.sp else 21.sp, color = Kit.Ink)
                    Text("Pick a restaurant to work in, or set up a new one.", fontFamily = Inter(), fontWeight = FontWeight.Medium,
                        fontSize = 14.sp, color = Kit.Muted)
                    state.summary?.let { summary ->
                        Row(Modifier.padding(top = 6.dp).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                            val plus = if (summary.partial) "+" else ""
                            BannerStat("${summary.restaurants}", if (summary.restaurants == 1L) "restaurant" else "restaurants")
                            BannerDivider()
                            BannerStat("${summary.open}$plus", "open now")
                            BannerDivider()
                            BannerStat("${summary.branches}$plus", if (summary.branches == 1L) "branch" else "branches")
                            BannerDivider()
                            BannerStat("${summary.staff}$plus", "staff")
                        }
                    }
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (state.canCreate) KitButton("Create a new restaurant", model::startCreate, icon = Icons.Outlined.Add)
                        if (state.superAdmin) KitButton("Co-Super-Admins", onCoSuperAdmins, icon = Icons.Outlined.ManageAccounts, style = ButtonStyle.SECONDARY)
                        if (state.canRead) KitButton(
                            if (state.pendingApplications > 0) "Applications (${state.pendingApplications})" else "Applications",
                            onApplications, icon = Icons.Outlined.Inbox, style = ButtonStyle.SECONDARY
                        )
                    }
                }
                if (!narrow) {
                    Image(painterResource(Res.drawable.workspace_restaurants), null, Modifier.padding(start = 24.dp).size(200.dp), contentScale = ContentScale.Fit)
                }
            }
        }
    }
}

@Composable
private fun BannerStat(value: String, label: String) {
    Column(Modifier.padding(end = 22.dp)) {
        Text(value, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Kit.Ink)
        Text(label, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Kit.Muted)
    }
}

@Composable
private fun BannerDivider() {
    Box(Modifier.padding(end = 22.dp).width(1.dp).fillMaxHeight().background(Kit.Border))
}

@Composable
private fun ListToolbar(query: String, onQuery: (String) -> Unit, state: RestaurantListState, model: RestaurantListScreenModel, narrow: Boolean) {
    if (state.reorder != null) {
        Text("Save or cancel the new order to return to search and filters.", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted)
        return
    }
    val search: @Composable (Modifier) -> Unit = { m -> SearchField(query, onQuery, m, placeholder = "Search by name, code or city", height = 46.dp) }
    val pills: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterPills(RestaurantFilter.entries.map { it to it.label }, state.filter, model::filter,
                modifier = if (narrow) Modifier.weight(1f) else Modifier)
            if (state.reorder == null) com.saporini.mobile_desktop.core.components.RefreshButton(state.loading, model::refresh)
        }
    }
    if (narrow) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            search(Modifier.fillMaxWidth())
            pills()
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Restaurants", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Kit.Ink)
            if (state.loaded) Text("${state.total}", Modifier.clip(RoundedCornerShape(50)).background(Kit.Tint).padding(horizontal = 10.dp, vertical = 3.dp),
                fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Kit.Ink)
            Spacer(Modifier.weight(1f))
            pills()
            search(Modifier.width(340.dp))
        }
    }
}

// ---- Cards ----

@Composable
private fun RestaurantCard(card: RestaurantCardDto, state: RestaurantListState, model: RestaurantListScreenModel, onOpen: () -> Unit) {
    PlaceCard(
        accentKey = card.id,
        title = card.name,
        place = listOfNotNull(card.city, card.country).joinToString(", ").ifBlank { "No address yet" },
        coverUrl = card.coverUrl,
        logoUrl = card.logoUrl,
        status = restaurantStatusLabel(card.status, card.active),
        statusColor = when {
            card.open -> Kit.Green
            card.status == "SUSPENDED" -> Kit.Amber
            else -> Kit.Grey
        },
        stats = listOf(
            "${card.branches}" to when {
                card.openBranches < card.branches -> "${card.openBranches} open"
                card.branches == 1L -> "branch"
                else -> "branches"
            },
            "${card.staff}" to "staff",
            card.currency to timeZoneLabel(card.timezone).substringBefore(" (")
        ),
        actionText = if (!card.open || card.openBranches == 0L) "Manage" else if (card.branches > 1) "Branches" else "Open",
        entering = state.entering == card.id,
        highlighted = state.justCreated == card.id,
        enabled = state.entering == null,
        canChangeCover = state.canManage,
        coverBusy = card.id in state.coverBusy,
        onCover = { picture -> model.uploadCover(card, picture.bytes, picture.fileName, picture.contentType) },
        onRemoveCover = { model.removeCover(card) },
        onCoverProblem = model::coverProblem,
        onOpen = onOpen,
        // Restaurants with no or one branch: add one right here (several branches: on their branch page).
        extraAction = if (state.canManage && card.branches <= 1) ({ AddPill("Add branch") { model.addBranch(card) } }) else null,
        onEdit = if (state.canManage) ({ model.startEdit(card) }) else null
    ) { accent ->
        if (card.ownerUserId == null) {
            // No owner yet (the second step was left for later).
            Box(Modifier.size(34.dp).clip(CircleShape).background(Kit.Tint), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.PersonAdd, null, Modifier.size(17.dp), tint = Kit.Muted)
            }
            Column(Modifier.weight(1f)) {
                Text("No owner yet", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink)
                if (state.canManage) Text("Add owner", Modifier.clip(RoundedCornerShape(4.dp)).clickable { model.addOwner(card) },
                    fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Kit.Green)
            }
        } else {
            InitialsAvatar(card.ownerName ?: card.ownerEmail ?: "?", color = accent.strong, size = 34.dp)
            Column(Modifier.weight(1f)) {
                Text(card.ownerName ?: card.ownerEmail ?: "Owner", fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (card.ownerInvitePending) when {
                    card.ownerInviteExpired -> FooterLine(Icons.Outlined.Mail, "Invitation expired", Kit.Danger)
                    card.ownerInviteDelivery == "FAILED" -> FooterLine(Icons.Outlined.Mail, "Invitation not delivered", Kit.Danger)
                    card.ownerInviteDelivery == "PENDING" || card.ownerInviteDelivery == "SENDING" ->
                        FooterLine(Icons.Outlined.Mail, "Sending invitation…", Kit.Amber)
                    else -> FooterLine(Icons.Outlined.Mail, "Invitation sent", Kit.Amber)
                }
                else Text("Owner", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
            }
        }
    }
}

@Composable
private fun FirstRestaurant(canCreate: Boolean, onCreate: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Image(painterResource(Res.drawable.workspace_restaurants), null, Modifier.size(150.dp), contentScale = ContentScale.Fit)
        Text("No restaurants yet", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Kit.Ink)
        Text("Create the first one. Its owner gets an email to set a password, and you can open it right away.",
            Modifier.widthIn(max = 420.dp), fontFamily = Inter(), fontSize = 14.sp, lineHeight = 20.sp, color = Kit.Muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (canCreate) KitButton("Create a new restaurant", onCreate, icon = Icons.Outlined.Add)
    }
}

/**
 * Setting up a restaurant in one dialog: step 1 the restaurant (its own call), a short "Restaurant created", then
 * step 2 its owner (a second call). Staying one dialog keeps the window from closing and reopening between steps.
 */
@Composable
private fun SetupDialog(state: RestaurantListState, model: RestaurantListScreenModel) {
    val draft = state.draft
    val created = state.created
    val owner = state.owner?.takeIf { it.afterCreate }
    val close = when {
        draft != null -> model::cancelCreate
        owner != null -> model::skipOwner
        else -> model::continueToOwner
    }
    AppDialog(
        title = if (owner != null) "Add the owner" else "Create a new restaurant",
        onDismiss = close,
        subtitle = when {
            owner != null -> "Now the person who owns ${owner.restaurantName}."
            created != null -> null
            else -> if (state.canManage) "First the restaurant, then its owner." else "Create the restaurant and its first branch."
        },
        busy = state.saving || owner?.saving == true,
        maxWidth = 720.dp,
        buttons = when {
            draft != null -> ({
                KitButton("Cancel", model::cancelCreate, style = ButtonStyle.SECONDARY, enabled = !state.saving)
                KitButton(if (state.canManage) "Next: the owner" else "Create restaurant", model::saveCreate, icon = Icons.AutoMirrored.Outlined.ArrowForward, loading = state.saving)
            })
            owner != null -> ({ OwnerButtons(owner, model::saveOwner, model::skipOwner) })
            else -> null
        }
    ) {
        if (state.canManage) SetupSteps(if (draft != null) 1 else 2)
        when {
            draft != null -> RestaurantFields(draft, state, model)
            created != null -> RestaurantCreated(created.name, model::continueToOwner, addOwnerNext = state.canManage)
            owner != null -> OwnerFields(owner, model::editOwner)
        }
    }
}

@Composable
private fun ColumnScope.RestaurantFields(draft: NewRestaurantDraft, state: RestaurantListState, model: RestaurantListScreenModel) {
    val problems = if (draft.showProblems) newRestaurantProblems(draft) else emptyMap()
    state.error?.let { MessageBar(it, MessageKind.ERROR) }
    if (state.canManage) CoverPicker(draft, model)
    FieldPair({ m -> TextInput(draft.name, { v -> model.editDraft { it.copy(name = v) } }, m, label = "Name", required = true,
        placeholder = "e.g. Saporini Tirana", icon = Icons.Outlined.Storefront, problem = problems["name"], maxLength = MAX_RESTAURANT_NAME) },
        { m -> TextInput(draft.legalName, { v -> model.editDraft { it.copy(legalName = v) } }, m, label = "Legal name", required = true,
            placeholder = "As registered, e.g. Saporini Sh.p.k.", icon = Icons.Outlined.Badge, problem = problems["legalName"], maxLength = MAX_LEGAL_NAME,
            hint = "Printed on receipts") })
    FieldPair({ m -> SearchableSelect(CURRENCIES.firstOrNull { it.code == draft.currency }, CURRENCIES, { it.label },
        { c -> model.editDraft { it.copy(currency = c.code) } }, m, label = "Currency", required = true, problem = problems["currency"],
        hint = "Prices and receipts use it. Changing it later converts the prices.") },
        { m -> SearchableSelect(draft.timezone.ifBlank { null }, TIME_ZONES, ::timeZoneLabel, { z -> model.editDraft { it.copy(timezone = z) } }, m,
            label = "Time zone", required = true, problem = problems["timezone"], detailOf = { it },
            hint = "Opening hours, bookings and reports follow it") })
    FieldPair({ m -> TextInput(draft.firstBranchName, { v -> model.editDraft { it.copy(firstBranchName = v) } }, m, label = "First branch",
        placeholder = "Main", icon = Icons.Outlined.Business, problem = problems["branch"], maxLength = MAX_BRANCH_NAME) },
        { m -> TextInput(draft.phone, { v -> model.editDraft { it.copy(phone = v) } }, m, label = "Phone", required = true,
            icon = Icons.Outlined.Phone, problem = problems["phone"], placeholder = "+355 69 000 0000", hint = "Guests see it on the website and in their emails") })
    TextInput(draft.email, { v -> model.editDraft { it.copy(email = v) } }, Modifier.fillMaxWidth(), label = "Restaurant email", required = true,
        icon = Icons.Outlined.Mail, problem = problems["email"], maxLength = MAX_EMAIL_TEXT, placeholder = "hello@restaurant.com",
        hint = "Guests see it on the website and in their emails")
}

/** The new restaurant's cover picture: how its card will look, with a picture or its own colour. */
@Composable
private fun CoverPicker(draft: NewRestaurantDraft, model: RestaurantListScreenModel) {
    CoverEditor(null, draft.cover, removed = false, name = draft.name, onPick = { picture -> model.editDraft { it.copy(cover = picture) } },
        onRemove = { model.editDraft { it.copy(cover = null) } }, onProblem = model::coverProblem)
}

/**
 * How the card's top will look: [picked] (not saved yet), otherwise the saved [currentUrl] (unless [removed]),
 * otherwise the card's own colour, with the initials tile. "Add cover picture" / "Change" / "Remove" in the corner.
 */
@Composable
private fun CoverEditor(
    currentUrl: String?,
    picked: com.saporini.mobile_desktop.platform.PickedPicture?,
    removed: Boolean,
    name: String,
    onPick: (com.saporini.mobile_desktop.platform.PickedPicture) -> Unit,
    onRemove: () -> Unit,
    onProblem: (String) -> Unit
) {
    val pick = rememberPicturePicker(onPicked = onPick, onProblem = onProblem)
    val bitmap = remember(picked) { picked?.let { runCatching { it.bytes.decodeToImageBitmap() }.getOrNull() } }
    val shownUrl = currentUrl?.takeIf { !removed && picked == null }
    val hasPicture = bitmap != null || shownUrl != null
    val accent = accentOf(name.ifBlank { "new" })
    Box(Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, Kit.Border, RoundedCornerShape(14.dp))) {
        when {
            bitmap != null -> {
                Image(bitmap, "Cover picture", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent))))
            }
            else -> CoverBackground(shownUrl, accent)
        }
        Row(Modifier.align(Alignment.BottomStart).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LogoTile(null, name.ifBlank { "New restaurant" }, accent,
                Modifier.size(52.dp).shadow(4.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)), 17)
            if (!hasPicture) Text("The restaurant's card looks like this. Add a cover picture to tell it apart.", Modifier.widthIn(max = 300.dp),
                fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, color = Kit.Ink)
        }
        Row(Modifier.align(Alignment.TopEnd).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hasPicture) CoverChip("Remove", Icons.Outlined.Delete, Kit.Danger, onRemove)
            CoverChip(if (hasPicture) "Change" else "Add cover picture", Icons.Outlined.AddPhotoAlternate, Kit.Ink, pick)
        }
    }
}

/**
 * The pen on a restaurant's card, in two tabs saved separately: the restaurant (cover picture, name, contact) and its
 * owner. "Save" works once something changed; leaving a tab (or closing) with changes asks to save or discard them.
 */
@Composable
private fun EditRestaurantDialog(step: com.saporini.mobile_desktop.platform.EditRestaurantStep, model: RestaurantListScreenModel) {
    val problems = if (step.showProblems) com.saporini.mobile_desktop.platform.editProblems(step) else emptyMap()
    val ownerTab = step.tab == com.saporini.mobile_desktop.platform.EditTab.OWNER
    AppDialog(
        "Edit ${step.card.name}", model::cancelEdit,
        subtitle = "Currency, time zone, branches and tax details are in the restaurant's Restaurant app.",
        busy = step.saving, maxWidth = 720.dp,
        buttons = {
            KitButton("Cancel", model::cancelEdit, style = ButtonStyle.SECONDARY, enabled = !step.saving)
            KitButton(
                when {
                    !ownerTab -> "Save restaurant"
                    step.ownerPerson == null -> "Add owner and send the invitation"
                    else -> "Save owner"
                },
                model::saveEdit, icon = Icons.Outlined.Check, loading = step.saving,
                enabled = !step.loading && step.restaurant != null && step.changed
            )
        }
    ) {
        OverviewTabs(com.saporini.mobile_desktop.platform.EditTab.entries.map { it.label }, step.tab.label,
            { label -> com.saporini.mobile_desktop.platform.EditTab.entries.firstOrNull { it.label == label }?.let(model::editTab) },
            Modifier.fillMaxWidth())
        step.error?.let { MessageBar(it, MessageKind.ERROR) }
        if (step.loading) {
            Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Kit.Green) }
            return@AppDialog
        }
        if (step.restaurant == null) return@AppDialog
        if (!ownerTab) {
            CoverEditor(step.card.coverUrl, step.cover, step.removeCover, step.name,
                onPick = { picture -> model.changeEdit { it.copy(cover = picture, removeCover = false) } },
                onRemove = { model.changeEdit { it.copy(cover = null, removeCover = true) } },
                onProblem = model::coverProblem)
            FieldPair({ m -> TextInput(step.name, { v -> model.changeEdit { it.copy(name = v) } }, m, label = "Name", required = true,
                icon = Icons.Outlined.Storefront, problem = problems["name"], maxLength = MAX_RESTAURANT_NAME) },
                { m -> TextInput(step.legalName, { v -> model.changeEdit { it.copy(legalName = v) } }, m, label = "Legal name", required = true,
                    icon = Icons.Outlined.Badge, problem = problems["legalName"], maxLength = MAX_LEGAL_NAME, hint = "Printed on receipts") })
            FieldPair({ m -> TextInput(step.phone, { v -> model.changeEdit { it.copy(phone = v) } }, m, label = "Phone", required = true,
                icon = Icons.Outlined.Phone, problem = problems["phone"], hint = "Guests see it on the website and in their emails") },
                { m -> TextInput(step.email, { v -> model.changeEdit { it.copy(email = v) } }, m, label = "Restaurant email", required = true,
                    icon = Icons.Outlined.Mail, problem = problems["email"], maxLength = MAX_EMAIL_TEXT, hint = "Guests see it on the website and in their emails") })
        } else {
            val person = step.ownerPerson
            val owner = step.owner
            NoteBox(
                if (person == null) Icons.Outlined.PersonAdd else Icons.Outlined.Person,
                when {
                    person == null -> "${step.card.name} has no owner yet. Add them here; they get an email with a link to set their password."
                    !person.emailVerified -> "${person.displayName} hasn't set their password yet. You can still correct their email; a new invitation goes to the new address."
                    else -> "${person.displayName} has signed up. You can correct their name and phone; their email is theirs to change."
                },
                tone = if (person != null && !person.emailVerified) Kit.Amber else Kit.Ink
            )
            FieldPair({ m -> TextInput(owner.firstName, { v -> model.editOwnerFields { it.copy(firstName = v) } }, m, label = "First name",
                required = true, icon = Icons.Outlined.Person, problem = problems["owner.firstName"], maxLength = 50) },
                { m -> TextInput(owner.lastName, { v -> model.editOwnerFields { it.copy(lastName = v) } }, m, label = "Last name",
                    required = true, problem = problems["owner.lastName"], maxLength = 50) })
            FieldPair({ m -> TextInput(owner.email, { v -> model.editOwnerFields { it.copy(email = v) } }, m, label = "Email",
                required = true, icon = Icons.Outlined.Mail, problem = problems["owner.email"], maxLength = MAX_EMAIL_TEXT,
                enabled = !step.ownerEmailLocked,
                hint = when {
                    step.ownerEmailLocked -> "They signed up with it, so only they can change it"
                    person != null -> "Changing it sends a new invitation to the new address"
                    else -> "The invitation goes here, and they sign in with it"
                }) },
                { m -> TextInput(owner.phone, { v -> model.editOwnerFields { it.copy(phone = v) } }, m, label = "Phone", optional = true,
                    icon = Icons.Outlined.Phone, problem = problems["owner.phone"], maxLength = 30) })
            if (person == null) {
                TextInput(owner.username, { v -> model.editOwnerFields { it.copy(username = v, usernameTouched = true) } }, Modifier.fillMaxWidth(),
                    label = "Username", required = true, problem = problems["owner.username"], maxLength = 50,
                    hint = "They can sign in with it or with their email")
            }
        }
    }
    step.leaving?.let { leaving -> LeaveEditDialog(step, leaving, model) }
}

/** Leaving a tab (or the dialog) with changes: save them, throw them away, or stay. */
@Composable
private fun LeaveEditDialog(
    step: com.saporini.mobile_desktop.platform.EditRestaurantStep,
    leaving: com.saporini.mobile_desktop.platform.EditLeave,
    model: RestaurantListScreenModel
) {
    val what = if (step.tab == com.saporini.mobile_desktop.platform.EditTab.RESTAURANT) "the restaurant's details" else "the owner's details"
    val where = when (leaving) {
        is com.saporini.mobile_desktop.platform.EditLeave.Tab -> "before going to the ${leaving.tab.label.lowercase()}"
        com.saporini.mobile_desktop.platform.EditLeave.Close -> "before closing"
    }
    AppDialog("Save your changes?", model::stayEditing, maxWidth = 460.dp, buttons = {
        KitButton("Discard", model::discardAndLeave, style = ButtonStyle.SECONDARY)
        KitButton("Save", model::saveAndLeave, icon = Icons.Outlined.Check)
    }) {
        Text("You changed $what. Save them $where?", fontFamily = Inter(), fontSize = 14.sp, lineHeight = 20.sp, color = Kit.Ink)
    }
}

@Composable
private fun CoverChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.94f)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = tint)
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = tint)
    }
}

