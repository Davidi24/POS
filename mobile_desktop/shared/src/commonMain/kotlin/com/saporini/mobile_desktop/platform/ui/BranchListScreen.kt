package com.saporini.mobile_desktop.platform.ui

import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.offset
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
import com.saporini.mobile_desktop.core.i18n.Text
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

/**
 * "Choose a branch": opened from the restaurant list for a restaurant with several branches; choosing one opens the six apps.
 * The page says which restaurant you're inside (user, 2026-11-21) and its branch cards look unlike restaurant cards.
 */
data class BranchListScreen(
    val restaurantId: String,
    val restaurantName: String,
    val logoUrl: String? = null,
    val coverUrl: String? = null,
    val place: String? = null
) : Screen {
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
                    modifier = Modifier.weight(1f).fillMaxWidth().nestedScroll(hideBar.connection),
                    logoUrl = logoUrl, coverUrl = coverUrl, place = place
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
    modifier: Modifier = Modifier,
    logoUrl: String? = null,
    coverUrl: String? = null,
    place: String? = null
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
    // Shapes like the branch cards until the first answer, held a moment so a quick load doesn't flash them; a search keeps
    // the cards until its answer.
    val shapes = com.saporini.mobile_desktop.core.components.steadyLoading(
        state.canRead && ((!state.loaded && state.error == null) || (state.loading && state.cards.isEmpty())))
    // Where the "Add branch" tab sits on the right edge (dragged up and down, like Add restaurant).
    var addButtonPosition by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(.84f) }
    BoxWithConstraints(modifier, contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            GridCells.Adaptive(if (narrow) 300.dp else 460.dp),
            Modifier.widthIn(max = 1280.dp).fillMaxSize(),
            state = grid,
            contentPadding = PaddingValues(start = side, end = side, top = if (narrow) 4.dp else 10.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
                InsideRestaurantBand(state.restaurantName, logoUrl, coverUrl, place, state.restaurantId ?: state.restaurantName, narrow, onBack)
            }
            item(key = "heading", span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.AccountTree, null, Modifier.size(20.dp), tint = Kit.Green)
                    Text("Its branches", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Kit.Ink)
                    if (!state.loaded && state.error == null) FigureShape(28.dp)
                    if (state.loaded) {
                        Text("${state.allBranches}", Modifier.clip(RoundedCornerShape(50)).background(Kit.Tint).padding(horizontal = 10.dp, vertical = 3.dp),
                            fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Kit.Ink, translate = false)
                        Text(if (state.openBranches == 1L) "1 open" else "${state.openBranches} open", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted)
                    }
                    Spacer(Modifier.weight(1f))
                    if (!narrow) Text("Choose the branch you work in", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted)
                    // The reload at the right end of this row, not on the restaurant's picture (user, 2026-11-21).
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
                shapes -> items(shapesFor(narrow), key = { "shape-$it" }) { ShapeCell { BranchCardShape(narrow) } }
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
                    items(state.cards, key = { it.id }) { card ->
                        Box(Modifier.animateItem(fadeInSpec = CardFadeIn, placementSpec = null, fadeOutSpec = tween(90))) {
                            BranchCard(card, state, model, state.restaurantId ?: state.restaurantName, narrow) { model.choose(card, onEntered) }
                        }
                    }
                    // The next branches come in as card shapes at the end, then fade into their cards.
                    if (state.loadingMore) items(if (narrow) 1 else 2, key = { "more-shape-$it" }) { ShapeCell { BranchCardShape(narrow) } }
                    else if (state.hasNext) item(key = "more", span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            if (state.error != null) RetryText(onClick = model::loadMore)
                            else com.saporini.mobile_desktop.core.components.KitButton("Show more branches", model::loadMore)
                        }
                    }
                }
            }
        }
        // Add branch as the green tab on the right edge, the same as Add restaurant (user, 2026-11-21).
        if (state.canChangeCover && state.canRead) {
            val maxOffset = with(androidx.compose.ui.platform.LocalDensity.current) {
                (maxHeight - com.saporini.mobile_desktop.core.components.edgeActionButtonHeight(narrow)).coerceAtLeast(0.dp).toPx()
            }
            com.saporini.mobile_desktop.core.components.RightEdgeActionButton(
                "Add branch", model::addBranch, enabled = state.entering == null, width = if (narrow) 108.dp else 132.dp, compact = narrow,
                modifier = Modifier.align(Alignment.TopEnd)
                    .offset { androidx.compose.ui.unit.IntOffset(0, kotlin.math.round(addButtonPosition * maxOffset).toInt()) }
                    .draggable(
                        orientation = androidx.compose.foundation.gestures.Orientation.Vertical,
                        state = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
                            if (maxOffset > 0f) addButtonPosition = (addButtonPosition + delta / maxOffset).coerceIn(0f, 1f)
                        }
                    )
            )
        }
    }
    state.branch?.let { step ->
        BranchFormDialog("Add a branch to ${step.restaurantName}", step.draft, step.saving, step.error, model::editBranch, model::saveBranch,
            model::cancelBranch, saveText = "Add branch")
    }
}


/**
 * Where you are: the restaurant's logo, name and place on its own picture, and the path back (Restaurants › name ›
 * Branches), so its branch cards below are never mistaken for restaurants (user, 2026-11-21).
 */
@Composable
private fun InsideRestaurantBand(
    name: String,
    logoUrl: String?,
    coverUrl: String?,
    place: String?,
    accentKey: String,
    narrow: Boolean,
    onBack: () -> Unit
) {
    val accent = accentOf(accentKey)
    val shape = RoundedCornerShape(20.dp)
    Surface(Modifier.fillMaxWidth(), shape = shape, color = Color.White, border = BorderStroke(1.dp, Kit.Border), shadowElevation = 2.dp) {
        Box(Modifier.fillMaxWidth().height(if (narrow) 132.dp else 128.dp)) {
            // The restaurant's picture on the right, fading into the white where the words are.
            if (!narrow) {
                Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.46f)) {
                    CoverBackground(coverUrl, accent)
                    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.White, Color.White.copy(alpha = 0.55f), Color.Transparent))))
                }
            } else {
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.White, accent.softer, accent.soft))))
            }
            Row(Modifier.fillMaxSize().padding(start = 12.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (narrow) 10.dp else 14.dp)) {
                PlainBackArrow("Back to the restaurants", onBack)
                Box(Modifier.shadow(6.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(3.dp)) {
                    RestaurantLogo(logoUrl, name, if (narrow) 52.dp else 66.dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(accent.strong).padding(horizontal = 9.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Outlined.Storefront, null, Modifier.size(12.dp), tint = Color.White)
                        Text("YOU'RE INSIDE THIS RESTAURANT", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 9.5.sp, letterSpacing = 0.6.sp,
                            color = Color.White, maxLines = 1)
                    }
                    Text(name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (narrow) 19.sp else 23.sp, color = Kit.Ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, translate = false)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Restaurants", Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onBack), fontFamily = Inter(),
                            fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Green)
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(14.dp), tint = Kit.Faint)
                        if (!narrow) {
                            Text(name, fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, translate = false)
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(14.dp), tint = Kit.Faint)
                        }
                        Text("Branches", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink)
                        if (place != null && !narrow) {
                            Text("·", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Faint, translate = false)
                            Icon(Icons.Outlined.Place, null, Modifier.size(13.dp), tint = Kit.Muted)
                            Text(place, fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, translate = false)
                        }
                    }
                }
            }
        }
    }
}


/**
 * A branch: a wide card with the picture on its left and a map pin, "BRANCH" and its code, in the restaurant's colour
 * (restaurant cards are tall, with the picture on top and a logo).
 */
@Composable
private fun BranchCard(card: BranchCardDto, state: BranchListState, model: BranchListScreenModel, restaurantKey: String, narrow: Boolean, onChoose: () -> Unit) {
    val accent = accentOf(restaurantKey)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val lift by animateDpAsState(if (hovered) 12.dp else 2.dp, label = "branch-lift")
    val shape = RoundedCornerShape(16.dp)
    val usable = card.open && state.restaurantOpen
    val entering = state.entering == card.id
    Surface(
        onClick = onChoose,
        enabled = usable && state.entering == null,
        interactionSource = interaction,
        modifier = Modifier.fillMaxWidth().shadow(lift, shape, ambientColor = Color(0x16000000), spotColor = Color(0x1C000000)),
        shape = shape, color = Color.White,
        border = BorderStroke(if (hovered) 1.5.dp else 1.dp, if (hovered) accent.strong.copy(alpha = 0.6f) else Kit.Border)
    ) {
        Row(Modifier.height(if (narrow) 168.dp else 158.dp)) {
            Box(Modifier.width(if (narrow) 112.dp else 168.dp).fillMaxHeight()) {
                CoverBackground(card.coverUrl, accent)
                if (state.canChangeCover) {
                    CoverButton(card.coverUrl != null, card.id in state.coverBusy,
                        { picture -> model.uploadCover(card, picture.bytes, picture.fileName, picture.contentType) },
                        { model.removeCover(card) }, model::coverProblem, Modifier.align(Alignment.TopStart).padding(8.dp))
                }
                // The map pin: this is a place of the restaurant, not a restaurant.
                Box(
                    Modifier.align(Alignment.BottomStart).padding(10.dp).size(36.dp).shadow(4.dp, CircleShape).clip(CircleShape).background(Color.White),
                    Alignment.Center
                ) {
                    Icon(Icons.Filled.Place, null, Modifier.size(20.dp), tint = accent.strong)
                }
            }
            Box(Modifier.width(4.dp).fillMaxHeight().background(accent.strong))
            Column(Modifier.weight(1f).padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("BRANCH", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp, color = accent.strong)
                    if (card.code.isNotBlank()) {
                        Text(card.code, Modifier.clip(RoundedCornerShape(5.dp)).background(accent.softer).border(1.dp, accent.soft, RoundedCornerShape(5.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp,
                            color = accent.strong, maxLines = 1, translate = false)
                    }
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(if (card.open) Kit.Green else Kit.Grey))
                        Text(branchStatusLabel(card.status, card.active), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp,
                            color = if (card.open) Kit.Green else Kit.Grey, maxLines = 1)
                    }
                }
                Text(card.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Kit.Ink, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, translate = false)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Outlined.Place, null, Modifier.size(14.dp), tint = Kit.Muted)
                    Text(listOfNotNull(card.street, card.city).joinToString(", ").ifBlank { "No address yet" }, fontFamily = Inter(), fontSize = 12.5.sp,
                        color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FooterLine(Icons.Outlined.Groups, if (card.staff == 1L) "1 person" else "${card.staff} staff")
                    FooterLine(Icons.Outlined.Devices, if (card.devices == 1L) "1 device" else "${card.devices} devices")
                    if (!narrow) card.phone?.let { FooterLine(Icons.Outlined.Phone, it) }
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(card.email ?: "", Modifier.weight(1f), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Faint, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, translate = false)
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(if (hovered && usable) accent.strong else if (usable) accent.softer else Kit.Tint)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (entering) {
                            CircularProgressIndicator(Modifier.size(14.dp), color = if (hovered) Color.White else accent.strong, strokeWidth = 2.dp)
                        } else {
                            Text(if (usable) "Work here" else "Unavailable", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.5.sp,
                                color = if (!usable) Kit.Muted else if (hovered) Color.White else accent.strong)
                            if (usable) Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(15.dp), tint = if (hovered) Color.White else accent.strong)
                        }
                    }
                }
            }
        }
    }
}
