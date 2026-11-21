package com.saporini.mobile_desktop.platform.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.saporini.mobile_desktop.core.components.KitDivider
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.users_owner
import org.jetbrains.compose.resources.painterResource
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.AssignmentInd
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import com.saporini.mobile_desktop.admin.people.PlatformRequestDto
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.CheckRow
import com.saporini.mobile_desktop.core.components.CheckState
import com.saporini.mobile_desktop.core.components.ConfirmDialog
import com.saporini.mobile_desktop.core.components.DoneBurst
import com.saporini.mobile_desktop.core.components.DoneMark
import com.saporini.mobile_desktop.core.components.InitialsAvatar
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.OverviewTabs
import com.saporini.mobile_desktop.core.components.RefreshButton
import com.saporini.mobile_desktop.core.components.ScreenMessages
import com.saporini.mobile_desktop.core.components.StatusChip
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.format.agoText
import com.saporini.mobile_desktop.core.format.dateTimeText
import com.saporini.mobile_desktop.core.i18n.Text
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.RemoteImage
import com.saporini.mobile_desktop.platform.AdminCardDto
import com.saporini.mobile_desktop.platform.PlatformRepository
import com.saporini.mobile_desktop.platform.platformMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import org.koin.compose.koinInject
import kotlin.time.Clock

/**
 * Super Admin: what restaurant owners ask (user, 2026-11-19): to remove one of their owners (owners never remove each other;
 * you check with that owner first, then remove them) or for a support admin (you pick one and they get the restaurant).
 * Redone 2026-11-21 in the Applications page's look (user: "make it better, the back arrow normal"): the top bar, a banner with
 * the figures, Waiting (N) | Answered (N) with the reload, and each request as a card with the restaurant's logo on its edge.
 */
object RestaurantRequestsScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val platform = koinInject<PlatformRepository>()
        val session = koinInject<com.saporini.mobile_desktop.core.session.SessionManager>()
        val user by session.currentUser.collectAsState()
        val workspaceModel = koinInject<com.saporini.mobile_desktop.workspace.ui.WorkspaceScreenModel>()
        val workspaceState by workspaceModel.state.collectAsState()
        LaunchedEffect(workspaceState.loggedOut) {
            if (workspaceState.loggedOut) navigator?.replaceAll(com.saporini.mobile_desktop.auth.ui.login.LoginScreen)
        }
        val scope = rememberCoroutineScope()
        var list by remember { mutableStateOf<List<PlatformRequestDto>?>(null) }
        var admins by remember { mutableStateOf<List<AdminCardDto>>(emptyList()) }
        var waitingOnly by remember { mutableStateOf(true) }
        // Pages of twelve, four rows of three (user, 2026-11-21: "pagination"); a tab starts on its first page.
        var page by remember(waitingOnly) { mutableStateOf(0) }
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var notice by remember { mutableStateOf<String?>(null) }
        var handling by remember { mutableStateOf<PlatformRequestDto?>(null) }
        var busy by remember { mutableStateOf(false) }
        var dialogError by remember { mutableStateOf<String?>(null) }
        var done by remember { mutableStateOf<DoneMark?>(null) }
        var reload by remember { mutableStateOf(0) }
        // Every request at once (the server keeps the last 200); both tabs and the figures come from it.
        LaunchedEffect(reload) {
            loading = true
            try {
                list = platform.platformRequests(false)
                error = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = platformMessage(e, write = false)
            } finally {
                loading = false
            }
        }
        LaunchedEffect(reload) {
            admins = runCatching { platform.admins().filter { it.kind == "SUPPORT" && it.active } }.getOrDefault(admins)
        }
        // The Super Admin sees and does everything; a support admin sees the requests given to them and answers them.
        val superAdmin = com.saporini.mobile_desktop.core.session.isSuperAdmin(user)
        // Runs one answer; the card changes at once (an answered one leaves Waiting) and the list is read again behind it.
        val act: (PlatformRequestDto, String, suspend () -> PlatformRequestDto) -> Unit = { request, doneText, call ->
            busy = true
            dialogError = null
            scope.launch {
                try {
                    val changed = call()
                    handling = null
                    list = list?.map { if (it.id == request.id) changed else it }
                    done = DoneMark((done?.tick ?: 0) + 1, doneText)
                    reload++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    dialogError = platformMessage(e, write = true)
                    if (e is com.saporini.mobile_desktop.core.network.ApiException && (e.status == 409 || e.status == 404)) reload++
                } finally {
                    busy = false
                }
            }
        }
        val answer: (PlatformRequestDto, Boolean, String?, String?, String) -> Unit = { request, approve, note, helperId, doneText ->
            act(request, doneText) { platform.decideRequest(request.id, approve, note, helperId) }
        }
        ScreenMessages(error, notice, onClear = { error = null; notice = null })
        BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFFFCFCFB))) {
            val narrow = maxWidth < 760.dp
            com.saporini.mobile_desktop.workspace.ui.PageDecorations(Modifier.fillMaxSize(), narrow)
            val hideBar = com.saporini.mobile_desktop.core.components.rememberHideOnScroll(narrow)
            Column(Modifier.fillMaxSize()) {
                com.saporini.mobile_desktop.core.components.HideOnScrollBar(hideBar) {
                    com.saporini.mobile_desktop.workspace.ui.PickerTopBar(user, workspaceState.isLoggingOut, workspaceModel::logout, compact = narrow)
                }
                val side = if (narrow) 16.dp else 36.dp
                val all = list
                val waiting = all?.filter { it.status == "PENDING" }
                val answeredList = all?.filter { it.status != "PENDING" }
                val shown = if (waitingOnly) waiting else answeredList
                val pages = shown?.let { ((it.size + RequestsPerPage - 1) / RequestsPerPage).coerceAtLeast(1) } ?: 1
                // Answering the last one of a page shows the page before.
                val onPage = page.coerceAtMost(pages - 1)
                val pageItems = shown?.drop(onPage * RequestsPerPage)?.take(RequestsPerPage)
                val grid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                LaunchedEffect(onPage) { if (grid.firstVisibleItemIndex > 2) grid.scrollToItem(2) }
                val full: (LazyGridItemSpanScope.() -> GridItemSpan) = { GridItemSpan(maxLineSpan) }
                Box(Modifier.weight(1f).fillMaxWidth().nestedScroll(hideBar.connection), contentAlignment = Alignment.TopCenter) {
                    LazyVerticalGrid(
                        GridCells.Adaptive(if (narrow) 300.dp else 340.dp),
                        Modifier.widthIn(max = 1280.dp).fillMaxSize(),
                        state = grid,
                        contentPadding = PaddingValues(start = side, end = side, top = 4.dp, bottom = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        item(key = "banner", span = full) { RequestsBanner(waiting, answeredList, superAdmin, narrow) { navigator?.pop() } }
                        item(key = "controls", span = full) { Column {
                            val waitingTab = waiting?.let { "Open (${it.size})" } ?: "Open"
                            val answeredTab = answeredList?.let { "Solved (${it.size})" } ?: "Solved"
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OverviewTabs(listOf(waitingTab, answeredTab), if (waitingOnly) waitingTab else answeredTab, { waitingOnly = it == waitingTab },
                                    if (narrow) Modifier.weight(1f) else Modifier.width(320.dp), height = 40.dp)
                                if (!narrow) {
                                    Spacer(Modifier.weight(1f))
                                    RequestsPager(onPage, pages, shown?.size ?: 0) { page = it }
                                }
                                RefreshButton(loading, { reload++ }, size = 40.dp)
                            }
                            if (narrow) RequestsPager(onPage, pages, shown?.size ?: 0, Modifier.fillMaxWidth().padding(top = 10.dp)) { page = it }
                        } }
                        when {
                            shown == null -> item(key = "loading", span = full) {
                                Box(Modifier.fillMaxWidth().padding(top = 40.dp), Alignment.Center) { CircularProgressIndicator(color = Kit.Green) }
                            }
                            shown.isEmpty() -> item(key = "empty", span = full) {
                                Box(Modifier.fillMaxWidth().padding(top = 24.dp), Alignment.Center) {
                                    OverviewEmpty(if (waitingOnly) "Nothing open" else "Nothing solved yet",
                                        "Owners ask here to remove another owner, or for a support admin.", Icons.Outlined.MarkEmailUnread)
                                }
                            }
                            else -> items(pageItems.orEmpty(), key = { it.id }) { request ->
                                Box(Modifier.animateItem(fadeInSpec = tween(220), placementSpec = tween(340, easing = FastOutSlowInEasing), fadeOutSpec = tween(220))) {
                                    RequestCard(request, admins,
                                        onHandle = { dialogError = null; handling = request },
                                        onStart = { act(request, "In progress") { platform.startRequest(request.id) } })
                                }
                            }
                        }
                        if (pages > 1) item(key = "pages", span = full) {
                            Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                                RequestsPager(onPage, pages, shown?.size ?: 0) { page = it }
                            }
                        }
                    }
                }
            }
        }
        handling?.let { request ->
            HandleRequestDialog(request, admins, superAdmin, busy, dialogError,
                onDismiss = { if (!busy) handling = null },
                onRemove = { note -> answer(request, true, note, null, "${request.targetName ?: "The owner"} was removed") },
                onSolve = { note -> act(request, "Marked as solved") { platform.solveRequest(request.id, note) } },
                onAssign = { admin, note, give ->
                    act(request, when {
                        admin == null -> "Taken back"
                        give && !isRemoval(request) -> "${admin.name} takes care of ${request.restaurantName ?: "the restaurant"} now"
                        else -> "Given to ${admin.name}"
                    }) { platform.assignRequest(request.id, admin?.id, note, give) }
                })
        }
        DoneBurst(done)
    }
}

private fun isRemoval(request: PlatformRequestDto) = request.kind == "REMOVE_OWNER"

/** The page's top, like the Applications page: what it is, what waits, and the owner figure. */
@Composable
private fun RequestsBanner(waiting: List<PlatformRequestDto>?, answered: List<PlatformRequestDto>?, superAdmin: Boolean, narrow: Boolean, onBack: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border), shadowElevation = 3.dp) {
        Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color.White, Color(0xFFF3F7F0), Color(0xFFE6EFE1))))) {
            Canvas(Modifier.matchParentSize()) {
                val center = Offset(size.width - 150.dp.toPx(), size.height * 0.55f)
                drawCircle(Kit.Green.copy(alpha = 0.06f), radius = 170.dp.toPx(), center = center)
                drawCircle(Kit.Green.copy(alpha = 0.07f), radius = 110.dp.toPx(), center = center)
            }
            Row(Modifier.padding(start = if (narrow) 12.dp else 20.dp, end = if (narrow) 16.dp else 28.dp, top = if (narrow) 16.dp else 22.dp,
                bottom = if (narrow) 18.dp else 22.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.saporini.mobile_desktop.core.ui.HoverTooltip("Back to the restaurants") {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to the restaurants",
                                Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onBack).padding(5.dp), tint = Kit.Ink)
                        }
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(Kit.Green).padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Outlined.MarkEmailUnread, null, Modifier.size(13.dp), tint = Color.White)
                            Text("OWNERS ASK", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp, color = Color.White)
                        }
                    }
                    Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Requests", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (narrow) 19.sp else 23.sp, color = Kit.Ink)
                        Text(if (superAdmin) "What restaurant owners ask you: to remove one of their owners, or for a support admin to help them."
                            else "What restaurant owners asked, given to you by the Super Admin.",
                            fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 13.5.sp, lineHeight = 19.sp, color = Kit.Muted)
                        Row(Modifier.padding(top = 6.dp).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                            // Where the tickets stand.
                            RequestStage.entries.forEachIndexed { index, stage ->
                                if (index > 0) FigureLine()
                                val count = if (stage == RequestStage.SOLVED) answered?.size else waiting?.count { stageOf(it) == stage }
                                Figure(count?.toString() ?: "…", stage.label.lowercase(), if ((count ?: 0) > 0 && stage != RequestStage.SOLVED) stage.color else Kit.Ink)
                            }
                        }
                    }
                }
                if (!narrow) {
                    Image(painterResource(Res.drawable.users_owner), null, Modifier.padding(start = 24.dp).size(170.dp), contentScale = ContentScale.Fit)
                }
            }
        }
    }
}

@Composable
private fun Figure(value: String, label: String, color: Color) {
    Column(Modifier.padding(end = 20.dp)) {
        Text(value, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 22.sp, color = color, translate = false)
        Text(label, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Kit.Muted, maxLines = 1)
    }
}

@Composable
private fun FigureLine() {
    Box(Modifier.padding(end = 20.dp).width(1.dp).fillMaxHeight().background(Kit.Border))
}

/**
 * A request as a card: a band in the kind's colour (red: remove an owner, blue: needs a support admin) with when it was asked,
 * the restaurant's logo on its edge, who asked, what they wrote, who it's about (the owner to remove, or who looks after them
 * now), where it stands (Open → Assigned → In progress → Solved), and Start | Handle across the foot; a solved one shows
 * your answer instead. Nothing is declined (user, 2026-11-21: "don't make it decline, just solved").
 */
@Composable
private fun RequestCard(request: PlatformRequestDto, admins: List<AdminCardDto>, onHandle: () -> Unit, onStart: () -> Unit) {
    val removal = isRemoval(request)
    val zone = remember { TimeZone.currentSystemDefault() }
    val now = remember { Clock.System.now() }
    val color = if (removal) Kit.Danger else Kit.Blue
    val waiting = request.status == "PENDING"
    val name = request.restaurantName ?: "A restaurant"
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val lift by animateDpAsState(if (hovered) 12.dp else 2.dp, label = "request-lift")
    val shape = RoundedCornerShape(18.dp)
    Surface(
        Modifier.fillMaxWidth().hoverable(interaction).shadow(lift, shape, ambientColor = Color(0x16000000), spotColor = Color(0x1C000000)),
        shape = shape, color = Color.White,
        border = BorderStroke(1.dp, if (hovered) color.copy(alpha = 0.45f) else Kit.Border)
    ) {
        Box {
            Column {
                // The restaurant's own picture (user, 2026-11-21: "put the images here"), with the kind's colour along its foot.
                Box(Modifier.fillMaxWidth().height(116.dp)) {
                    CoverBackground(request.restaurantCoverUrl, accentOf(name))
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp).background(color))
                    Box(Modifier.align(Alignment.TopStart).padding(12.dp)) { KindPill(removal) }
                    Box(Modifier.align(Alignment.TopEnd).padding(12.dp)) { StagePill(stageOf(request)) }
                }
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 38.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Kit.Ink, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, translate = false)
                        Text(listOfNotNull(request.createdByName?.let { "Asked by $it" } ?: "Asked by an owner", dateTimeText(request.createdAt, zone))
                            .joinToString(" · "), fontFamily = Inter(), fontSize = 12.5.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    ProgressSteps(request)
                    // Given to a support admin: who has it, and the note for them.
                    request.assignedToName?.takeIf { waiting }?.let { who ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Purple.copy(alpha = 0.07f))
                                .border(1.dp, Kit.Purple.copy(alpha = 0.18f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Outlined.AssignmentInd, null, Modifier.size(18.dp), tint = Kit.Purple)
                            Column(Modifier.weight(1f)) {
                                Text("Assigned to $who", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                                request.assignNote?.let {
                                    Text(it, fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, translate = false)
                                }
                            }
                        }
                    }
                    Quote(request.note ?: "No note.")
                    // Who it's about.
                    if (removal) {
                        SubjectRow(Kit.Danger) {
                            InitialsAvatar(request.targetName ?: "?", color = Kit.Danger, size = 32.dp)
                            Column(Modifier.weight(1f)) {
                                Text(request.targetName ?: "An owner", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = Kit.Ink,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, translate = false)
                                Text(listOfNotNull("Owner to remove", request.targetEmail).joinToString(" · "), fontFamily = Inter(), fontSize = 11.5.sp,
                                    color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    } else {
                        val helpers = admins.filter { admin -> admin.allRestaurants || admin.restaurants.any { it.id == request.restaurantId } }
                        SubjectRow(if (helpers.isEmpty()) Kit.Amber else Kit.Blue) {
                            if (helpers.isEmpty()) {
                                Box(Modifier.size(32.dp).clip(CircleShape).background(Kit.Amber.copy(alpha = 0.14f)), Alignment.Center) {
                                    Icon(Icons.Outlined.SupportAgent, null, Modifier.size(17.dp), tint = Kit.Amber)
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                                    helpers.take(3).forEach { InitialsAvatar(it.name, Modifier.border(2.dp, Color.White, CircleShape), color = Kit.Blue, size = 32.dp) }
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    when (helpers.size) {
                                        0 -> "Nobody looks after them yet"
                                        1 -> "Looked after by ${helpers.first().name}"
                                        else -> "Looked after by ${helpers.size} support admins"
                                    },
                                    fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(if (helpers.isEmpty()) "Pick someone when you handle it" else "You can give them someone else too", fontFamily = Inter(),
                                    fontSize = 11.5.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (waiting) {
                        KitDivider()
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (request.startedAt == null) {
                                KitButton("Start", onStart, Modifier.weight(1f), style = ButtonStyle.SECONDARY, icon = Icons.Outlined.PlayArrow)
                            }
                            KitButton("Handle", onHandle, Modifier.weight(1f), icon = Icons.Outlined.Check)
                        }
                    } else {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Canvas).padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text("HOW IT WAS SOLVED", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp, color = Kit.Faint)
                            Text(request.decisionNote ?: when (request.status) {
                                    else -> "Solved, without a note."
                                },
                                fontFamily = Inter(), fontSize = 13.sp, color = Kit.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                translate = request.decisionNote == null)
                            Text(listOfNotNull(request.decidedByName?.let { "By $it" }, request.decidedAt?.let { dateTimeText(it, zone) }).joinToString(" · "),
                                fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Muted, maxLines = 1)
                        }
                    }
                }
            }
            // The restaurant's logo on the band's edge, as on the restaurant cards.
            Box(
                Modifier.padding(start = 20.dp, top = 80.dp).size(64.dp).shadow(6.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp)).background(Color.White).padding(3.dp)
            ) {
                LogoTile(request.restaurantLogoUrl, name, accentOf(name), Modifier.fillMaxSize().clip(RoundedCornerShape(15.dp)), 21)
            }
        }
    }
}

private const val RequestsPerPage = 12

/** "1–12 of 30" with ‹ Page 1 of 3 ›, as on Applications and Users. */
@Composable
private fun RequestsPager(page: Int, pages: Int, total: Int, modifier: Modifier = Modifier, onPage: (Int) -> Unit) {
    if (total == 0) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        Text("${page * RequestsPerPage + 1}–${minOf((page + 1) * RequestsPerPage, total)} of $total", Modifier.padding(end = 6.dp), fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted, translate = false)
        PageArrow(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous page", page > 0) { onPage(page - 1) }
        Text("Page ${page + 1} of $pages", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink)
        PageArrow(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next page", page < pages - 1) { onPage(page + 1) }
    }
}

@Composable
private fun PageArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, modifier = Modifier.size(34.dp), shape = RoundedCornerShape(8.dp), color = Color.White,
        border = BorderStroke(1.dp, Kit.Border)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, Modifier.size(18.dp), tint = if (enabled) Kit.Ink else Kit.Faint) }
    }
}

@Composable
private fun SubjectRow(color: Color, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.06f)).border(1.dp, color.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), content = content
    )
}

@Composable
private fun KindPill(removal: Boolean) {
    val color = if (removal) Kit.Danger else Kit.Blue
    Row(
        // White, so it reads on the restaurant's picture.
        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.95f)).padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(if (removal) Icons.Outlined.PersonRemove else Icons.Outlined.SupportAgent, null, Modifier.size(13.dp), tint = color)
        Text(if (removal) "Remove an owner" else "Needs a support admin", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp,
            color = color, maxLines = 1)
    }
}

/** What the owner wrote, as a quote. */
@Composable
private fun Quote(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Canvas).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Outlined.FormatQuote, null, Modifier.size(18.dp), tint = Kit.Faint)
        Text(text, fontFamily = Inter(), fontSize = 13.sp, lineHeight = 19.sp, color = Kit.Ink, minLines = 2, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

private enum class HandleWay { REMOVE, ASSIGN, SOLVED }

/** Where a request stands (user, 2026-11-21: "show the progress of the ticket: open, in progress, assigned…"). */
private enum class RequestStage(val label: String, val color: Color) {
    OPEN("Open", Kit.Amber),
    ASSIGNED("Assigned", Kit.Purple),
    IN_PROGRESS("In progress", Kit.Blue),
    SOLVED("Solved", Kit.Green)
}

private fun stageOf(request: PlatformRequestDto) = when {
    request.status != "PENDING" -> RequestStage.SOLVED
    request.startedAt != null -> RequestStage.IN_PROGRESS
    request.assignedToId != null -> RequestStage.ASSIGNED
    else -> RequestStage.OPEN
}

@Composable
private fun StagePill(stage: RequestStage) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.95f)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(stage.color))
        Text(stage.label, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = stage.color, maxLines = 1)
    }
}

/** Open → Assigned → In progress → Solved: the steps reached are filled, the one it's at is ringed; a step skipped stays faint. */
@Composable
private fun ProgressSteps(request: PlatformRequestDto) {
    val now = stageOf(request)
    val reached = setOfNotNull(
        RequestStage.OPEN,
        RequestStage.ASSIGNED.takeIf { request.assignedToId != null },
        RequestStage.IN_PROGRESS.takeIf { request.startedAt != null || now == RequestStage.SOLVED },
        RequestStage.SOLVED.takeIf { now == RequestStage.SOLVED }
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        RequestStage.entries.forEachIndexed { index, stage ->
            val on = stage in reached
            val current = stage == now
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.Center) {
                    // The line to the next and from the one before.
                    Row(Modifier.fillMaxWidth()) {
                        Box(Modifier.weight(1f).height(2.dp).background(if (index == 0) Color.Transparent else if (on) now.color.copy(alpha = 0.55f) else Kit.Border))
                        Box(Modifier.weight(1f).height(2.dp).background(
                            if (index == RequestStage.entries.lastIndex) Color.Transparent
                            else if (RequestStage.entries[index + 1] in reached) now.color.copy(alpha = 0.55f) else Kit.Border))
                    }
                    Box(
                        Modifier.size(if (current) 16.dp else 12.dp).clip(CircleShape)
                            .background(if (on) now.color else Color.White)
                            .border(if (current) 3.dp else 1.5.dp, if (current) now.color.copy(alpha = 0.3f) else if (on) now.color else Kit.Border, CircleShape)
                    )
                }
                Text(stage.label, fontFamily = Inter(), fontWeight = if (current) FontWeight.Bold else FontWeight.Medium, fontSize = 11.sp,
                    color = if (current) Kit.Ink else if (on) Kit.Muted else Kit.Faint, maxLines = 1)
            }
        }
    }
}

/**
 * Handling one request (user, 2026-11-21: "I can mark it solved, or assign it to someone"; then "only one: assign it, and
 * ask to put them in their restaurant"):
 * - Remove the owner (removal requests): check with that owner first (their email and phone are here, and the asker's),
 *   tick that you did, then remove.
 * - Assign to someone (Super Admin): a support admin takes the request; "Put them in <restaurant>?" also gives them that
 *   restaurant. On a "needs a support admin" request, putting them in answers it (the owner is told who takes care of them).
 * - Mark as solved: taken care of another way; nothing changes in the app, the owner sees your note.
 * A support admin it was given to can remove an owner (when allowed) or mark it solved. Nothing is declined.
 */
@Composable
private fun HandleRequestDialog(
    request: PlatformRequestDto,
    admins: List<AdminCardDto>,
    superAdmin: Boolean,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onRemove: (String?) -> Unit,
    onSolve: (String?) -> Unit,
    onAssign: (AdminCardDto?, String?, Boolean) -> Unit
) {
    val removal = isRemoval(request)
    val ways = listOfNotNull(HandleWay.REMOVE.takeIf { removal }, HandleWay.ASSIGN.takeIf { superAdmin }, HandleWay.SOLVED)
    var way by remember(request.id) { mutableStateOf(ways.first()) }
    var checked by remember(request.id) { mutableStateOf(false) }
    var helper by remember(request.id) { mutableStateOf<String?>(null) }
    var putIn by remember(request.id) { mutableStateOf(true) }
    var note by remember(request.id) { mutableStateOf("") }
    var assignNote by remember(request.id) { mutableStateOf("") }
    val target = request.targetName ?: "this owner"
    val restaurant = request.restaurantName ?: "the restaurant"
    val assignee = admins.firstOrNull { it.id == helper }
    // Whether the picked support admin already works in this restaurant (every restaurant, or this one given to them).
    val hasIt = assignee != null && (assignee.allRestaurants || assignee.restaurants.any { it.id == request.restaurantId })
    val give = assignee != null && !hasIt && putIn
    AppDialog(
        "Handle the request",
        onDismiss,
        subtitle = listOfNotNull(restaurant, request.createdByName?.let { "asked by $it" }).joinToString(" · "),
        busy = busy, maxWidth = 680.dp,
        buttons = {
            KitButton("Cancel", onDismiss, style = ButtonStyle.SECONDARY, enabled = !busy)
            when (way) {
                HandleWay.REMOVE -> KitButton("Remove $target", { onRemove(note.trim().ifEmpty { null }) }, style = ButtonStyle.DANGER,
                    icon = Icons.Outlined.PersonRemove, enabled = checked, loading = busy)
                HandleWay.SOLVED -> KitButton("Mark as solved", { onSolve(note.trim().ifEmpty { null }) }, icon = Icons.Outlined.TaskAlt, loading = busy)
                HandleWay.ASSIGN -> KitButton(assignee?.let { "Assign to ${it.name}" } ?: "Assign",
                    { assignee?.let { onAssign(it, assignNote.trim().ifEmpty { null }, give) } },
                    icon = Icons.Outlined.AssignmentInd, enabled = assignee != null && (assignee.id != request.assignedToId || give), loading = busy)
            }
        }
    ) {
        error?.let { MessageBar(it, MessageKind.ERROR) }
        request.note?.let { Quote(it) }
        // How to handle it.
        val narrowDialog = com.saporini.mobile_desktop.core.components.LocalNarrowDialog.current
        val tiles: @Composable (Modifier) -> Unit = { m ->
            ways.forEach { option ->
                WayTile(
                    when (option) {
                        HandleWay.REMOVE -> Icons.Outlined.PersonRemove
                        HandleWay.ASSIGN -> Icons.Outlined.AssignmentInd
                        HandleWay.SOLVED -> Icons.Outlined.TaskAlt
                    },
                    when (option) {
                        HandleWay.REMOVE -> "Remove the owner"
                        HandleWay.ASSIGN -> "Assign to someone"
                        HandleWay.SOLVED -> "Mark as solved"
                    },
                    when (option) {
                        HandleWay.REMOVE -> "Do it now, here"
                        HandleWay.ASSIGN -> "A support admin takes it over"
                        HandleWay.SOLVED -> "It was taken care of another way"
                    },
                    way == option, m
                ) { way = option }
            }
        }
        if (narrowDialog) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { tiles(Modifier.fillMaxWidth()) }
        else Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) { tiles(Modifier.weight(1f).fillMaxHeight()) }
        KitDivider()
        when (way) {
            HandleWay.REMOVE -> {
                StepTitle(1, "Check with them first")
                Text("Owners never remove each other, so make sure it's right before you do it.", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted)
                PersonContact(request.targetName ?: "The owner", "Owner to remove", request.targetEmail, request.targetPhone, Kit.Danger)
                PersonContact(request.createdByName ?: "The owner who asked", "Asked for it", request.createdByEmail, request.createdByPhone, Kit.Green)
                StepTitle(2, "Remove them")
                CheckRow("I talked to $target and it's right", if (checked) CheckState.ON else CheckState.OFF, { checked = !checked }, strong = true)
                Text("$target is removed from $restaurant and signed out everywhere. What they did stays.", fontFamily = Inter(), fontSize = 13.sp,
                    lineHeight = 19.sp, color = Kit.Muted)
            }
            HandleWay.SOLVED -> Text("Nothing changes in the app: the request closes as solved, and $restaurant sees your note.", fontFamily = Inter(),
                fontSize = 13.sp, lineHeight = 19.sp, color = Kit.Muted)
            HandleWay.ASSIGN -> {
                request.assignedToName?.let { who ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Purple.copy(alpha = 0.07f)).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Outlined.AssignmentInd, null, Modifier.size(18.dp), tint = Kit.Purple)
                        Text("Now with $who", Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink)
                        KitButton("Take it back", { onAssign(null, null, false) }, style = ButtonStyle.SECONDARY, enabled = !busy)
                    }
                }
                StepTitle(1, "Who takes it over?")
                if (admins.isEmpty()) {
                    MessageBar("You have no support admins yet. Add one on the Admins page first.", MessageKind.WARNING)
                }
                admins.forEach { admin ->
                    val already = admin.allRestaurants || admin.restaurants.any { it.id == request.restaurantId }
                    AdminChoice(admin, when {
                        admin.id == request.assignedToId -> "Has it now"
                        already -> "In $restaurant"
                        else -> null
                    }, helper == admin.id) { helper = admin.id }
                }
                assignee?.let { who ->
                    StepTitle(2, "Put ${who.name} in $restaurant?")
                    if (hasIt) {
                        Text("${who.name} already takes care of $restaurant.", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            com.saporini.mobile_desktop.core.components.ChoiceChip("Yes, put them in", putIn, { putIn = true })
                            com.saporini.mobile_desktop.core.components.ChoiceChip("No, only the request", !putIn, { putIn = false })
                        }
                    }
                    Text(
                        when {
                            !removal && give -> "${who.name} gets $restaurant and takes care of it. That solves the request: the owner is told."
                            give -> "${who.name} gets $restaurant and the request, and answers it from their Requests page."
                            else -> "${who.name} gets only the request and answers it from their Requests page."
                        },
                        fontFamily = Inter(), fontSize = 13.sp, lineHeight = 19.sp, color = Kit.Muted
                    )
                }
                TextInput(assignNote, { assignNote = it.take(2000) }, label = "Note for them", optional = true, maxLength = 2000,
                    placeholder = "What to check or do")
            }
        }
        if (way != HandleWay.ASSIGN) {
            TextInput(note, { note = it.take(2000) }, label = "Note for the restaurant", optional = true, maxLength = 2000,
                placeholder = if (way == HandleWay.SOLVED) "What was done" else "Done, he's off the restaurant")
        }
    }
}

/** One way of handling a request, picked like a tab. */
@Composable
private fun WayTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, line: String, selected: Boolean, modifier: Modifier, onPick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.clip(shape).background(if (selected) Kit.GreenSoft else Color.White)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) Kit.Green else Kit.Border, shape).clickable(onClick = onPick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(if (selected) Kit.Green else Kit.Tint), Alignment.Center) {
            Icon(icon, null, Modifier.size(18.dp), tint = if (selected) Color.White else Kit.Muted)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(line, fontFamily = Inter(), fontSize = 11.5.sp, lineHeight = 15.sp, color = Kit.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StepTitle(number: Int, text: String) {
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(Kit.Green), Alignment.Center) {
            Text("$number", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White, translate = false)
        }
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink)
    }
}

@Composable
private fun PersonContact(name: String, role: String, email: String?, phone: String?, color: Color) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Kit.Border, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        InitialsAvatar(name, color = color, size = 38.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(role, Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.10f)).padding(horizontal = 8.dp, vertical = 2.dp),
                    fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = color, maxLines = 1)
            }
            FooterLine(Icons.Outlined.Mail, email ?: "No email")
            FooterLine(Icons.Outlined.Phone, phone ?: "No phone")
        }
    }
}

@Composable
private fun AdminChoice(admin: AdminCardDto, tag: String?, selected: Boolean, onPick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(if (selected) Kit.GreenSoft else Color.White)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) Kit.Green else Kit.Border, shape).clickable(onClick = onPick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, Modifier.size(20.dp),
            tint = if (selected) Kit.Green else Kit.Faint)
        if (admin.photoUrl != null) {
            RemoteImage(admin.photoUrl, null, Modifier.size(36.dp).clip(CircleShape), contentScale = ContentScale.Crop) {
                InitialsAvatar(admin.name, color = Kit.Blue, size = 36.dp)
            }
        } else InitialsAvatar(admin.name, color = Kit.Blue, size = 36.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(admin.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    admin.allRestaurants -> "Takes care of every restaurant"
                    admin.restaurants.isEmpty() -> "No restaurants yet"
                    admin.restaurants.size == 1 -> "Takes care of ${admin.restaurants.first().name}"
                    else -> "Takes care of ${admin.restaurants.size} restaurants"
                },
                fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        tag?.let { StatusChip(it, Kit.Green) }
    }
}
