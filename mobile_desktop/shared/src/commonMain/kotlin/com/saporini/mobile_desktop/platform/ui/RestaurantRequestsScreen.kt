package com.saporini.mobile_desktop.platform.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import com.saporini.mobile_desktop.platform.RequestEventDto
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.History
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.animation.core.VectorConverter
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Block
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import com.saporini.mobile_desktop.core.components.KitDivider
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
import com.saporini.mobile_desktop.core.components.InitialsAvatar
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
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
 * Super Admin: what restaurants ask (user, 2026-11-19): to remove one of their owners (owners never remove each other; you
 * check with that owner first, then remove them), for a support admin, or (since 2026-11-21) about a problem, which anyone
 * working there sends from the profile menu with a message. Redone 2026-11-21 as a board like the kitchen's (user: "make the
 * ticket more like the kitchen status: request, assigned, in progress, solved"): Open | Assigned | In progress | Solved, each
 * request a ticket with its next step (Assign → Start → Solve / Handle), a click opens it big. A support admin sees the ones
 * given to them.
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
        // The board's search and kind filter, and the stage a phone shows.
        var query by remember { mutableStateOf("") }
        var kindFilter by remember { mutableStateOf<String?>(null) }
        var phoneStage by remember { mutableStateOf(RequestStage.OPEN) }
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var notice by remember { mutableStateOf<String?>(null) }
        var handling by remember { mutableStateOf<Pair<PlatformRequestDto, HandleWay?>?>(null) }
        var openId by remember { mutableStateOf<String?>(null) }
        // The card whose history is open (its clock button).
        var historyOf by remember { mutableStateOf<PlatformRequestDto?>(null) }
        // "New ticket": the Super Admin opens one themselves (user, 2026-11-21).
        var newTicket by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var dialogError by remember { mutableStateOf<String?>(null) }
        // The card that just moved: its frame lights up for a few seconds (user, 2026-11-21: "not the animation in the centre,
        // just emphasize the card's border").
        var flashId by remember { mutableStateOf<String?>(null) }
        var flashTick by remember { mutableStateOf(0) }
        LaunchedEffect(flashTick) {
            if (flashTick > 0) {
                kotlinx.coroutines.delay(2_600)
                flashId = null
            }
        }
        var reload by remember { mutableStateOf(0) }
        // Every request at once (the server keeps the last 200); the four columns come from it.
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
        // The Super Admin sees and does everything; a support admin sees the requests given to them and answers them, and with
        // "Assign requests" (ticked by the Super Admin) sees them all and gives them out too.
        val superAdmin = com.saporini.mobile_desktop.core.session.isSuperAdmin(user)
        val me = user?.id
        val canAssign = superAdmin || user?.permissions.orEmpty().contains("REQUESTS_ASSIGN")
        val rules = BoardRules(superAdmin, canAssign, me)
        // You, first in the list of who can take a request.
        val self = user?.let { AdminCardDto(id = it.id, name = listOfNotNull(it.firstName, it.lastName).joinToString(" ").ifBlank { it.username }) }
        var refusal by remember { mutableStateOf<String?>(null) }
        // Runs one step; the card moves at once ([moved], when known) and the list is read again behind it. A failure shows
        // where it was started and puts the card back.
        val act: (PlatformRequestDto, String, PlatformRequestDto?, suspend () -> PlatformRequestDto) -> Unit = { request, doneText, moved, call ->
            busy = true
            dialogError = null
            if (moved != null) {
                list = list?.map { if (it.id == request.id) moved else it }
                flashId = request.id
                flashTick++
            }
            scope.launch {
                try {
                    val changed = call()
                    handling = null
                    list = list?.map { if (it.id == request.id) changed else it }
                    flashId = request.id
                    flashTick++
                    reload++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val message = platformMessage(e, write = true)
                    if (handling != null || openId != null) dialogError = message else error = message
                    if (moved != null || (e is com.saporini.mobile_desktop.core.network.ApiException && (e.status == 409 || e.status == 404))) reload++
                } finally {
                    busy = false
                }
            }
        }
        val answer: (PlatformRequestDto, Boolean, String?, String?, String) -> Unit = { request, approve, note, helperId, doneText ->
            act(request, doneText, null) { platform.decideRequest(request.id, approve, note, helperId) }
        }
        val now = remember(list) { Clock.System.now().toString() }
        // Moves a ticket to [to] by the board's rules (user, 2026-11-21: "drag and drop based on rules, not anywhere"): giving
        // it to someone opens the picker, solving opens Solve (Handle for a removal); a move that isn't allowed says why.
        val move: (PlatformRequestDto, RequestStage) -> Unit = { request, to ->
            dialogError = null
            when (val check = rules.check(request, to)) {
                is MoveCheck.Refused -> refusal = check.reason
                MoveCheck.Same -> {}
                is MoveCheck.Allowed -> when (check.kind) {
                    MoveKind.ASSIGN -> handling = request to HandleWay.ASSIGN
                    MoveKind.START -> act(request, "In progress",
                        request.copy(startedAt = now, startedByName = self?.name, assignedToId = request.assignedToId ?: me,
                            assignedToName = request.assignedToName ?: self?.name, assignedAt = request.assignedAt ?: now)
                    ) { platform.startRequest(request.id) }
                    MoveKind.SOLVE -> handling = request to (if (isRemoval(request)) HandleWay.REMOVE else HandleWay.SOLVED)
                    MoveKind.TAKE_BACK -> act(request, "Back to open",
                        request.copy(assignedToId = null, assignedToName = null, assignedAt = null, assignNote = null, startedAt = null, startedByName = null)
                    ) { platform.assignRequest(request.id, null, null, false) }
                }
            }
        }
        // A card's button is its next step, when this person may take it.
        val step: (PlatformRequestDto) -> Unit = { request -> rules.next(request)?.let { move(request, it) } }
        val drag = remember { BoardDrag() }
        // Before the first answer a failure shows on the page with Try again, not as a toast.
        ScreenMessages(error.takeIf { list != null }, notice, onClear = { error = null; notice = null })
        BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFFFCFCFB))) {
            val narrow = maxWidth < 760.dp
            com.saporini.mobile_desktop.workspace.ui.PageDecorations(Modifier.fillMaxSize(), narrow)
            Column(Modifier.fillMaxSize()) {
                com.saporini.mobile_desktop.workspace.ui.PickerTopBar(user, workspaceState.isLoggingOut, workspaceModel::logout, compact = narrow)
                val side = if (narrow) 12.dp else 28.dp
                val all = list
                val text = query.trim()
                val shown = all?.filter { request ->
                    (kindFilter == null || request.kind == kindFilter) && (text.isEmpty() || listOfNotNull(request.restaurantName, request.createdByName,
                        request.note, request.targetName, request.assignedToName).any { it.contains(text, ignoreCase = true) })
                }
                val byStage = RequestStage.entries.associateWith { stage -> shown.orEmpty().filter { stageOf(it) == stage }.let { sortedFor(stage, it) } }
                // Shapes like the board until the first answer, held a moment so a quick load doesn't flash them.
                val shapes = com.saporini.mobile_desktop.core.components.steadyLoading(all == null && error == null)
                Column(Modifier.weight(1f).fillMaxWidth().padding(start = side, end = side, top = 4.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(if (narrow) 10.dp else 14.dp)) {
                    BoardBar(
                        superAdmin, narrow, open = all?.count { it.status == "PENDING" }, loading = loading,
                        query = query, onQuery = { query = it.take(100) },
                        filters = { size -> KindFilterButton(kindFilter, all.orEmpty(), size) { kindFilter = it } },
                        onReload = { reload++ }, onBack = { navigator?.pop() },
                        onNew = if (canAssign && all != null) ({ newTicket = true }) else null
                    )
                    when {
                        shapes -> BoardShape(narrow, Modifier.weight(1f).fillMaxWidth())
                        all == null -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                            com.saporini.mobile_desktop.core.components.PageState(com.saporini.mobile_desktop.core.components.PageStateKind.FAILED,
                                action = { com.saporini.mobile_desktop.core.components.RetryText(onClick = { error = null; reload++ }) })
                        }
                        narrow -> {
                            // A phone shows one column at a time, picked from the stages with their counts.
                            val tabs = RequestStage.entries.map { "${it.label} (${byStage[it].orEmpty().size})" }
                            OverviewTabs(tabs, tabs[phoneStage.ordinal], { label -> phoneStage = RequestStage.entries[tabs.indexOf(label)] },
                                Modifier.fillMaxWidth(), height = 40.dp)
                            BoardColumn(phoneStage, byStage[phoneStage].orEmpty(), rules, Modifier.weight(1f).fillMaxWidth(), showHead = false, flashId = flashId,
                                onOpen = { dialogError = null; openId = it.id }, onStep = step, onHistory = { historyOf = it })
                        }
                        else -> {
                            // Four columns like the kitchen board: Open, Assigned, In progress, Solved. On a narrower window they keep
                            // their width and the board slides sideways.
                            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                                val gap = 14.dp
                                val columnWidth = ((maxWidth - gap * 3) / 4).coerceAtLeast(270.dp)
                                val scroll = androidx.compose.foundation.rememberScrollState()
                                var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                                Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
                                    Row(Modifier.fillMaxSize().horizontalScroll(scroll, enabled = drag.request == null), horizontalArrangement = Arrangement.spacedBy(gap)) {
                                        RequestStage.entries.forEach { stage ->
                                            BoardColumn(stage, byStage[stage].orEmpty(), rules,
                                                Modifier.width(columnWidth).fillMaxHeight().onGloballyPositioned { drag.columns[stage] = it.boundsInRoot() },
                                                flashId = flashId, drag = drag, look = drag.lookOf(stage, rules),
                                                onOpen = { dialogError = null; openId = it.id }, onStep = step, onHistory = { historyOf = it },
                                                onDrop = { request ->
                                                    val target = drag.over
                                                    val check = target?.let { rules.check(request, it) }
                                                    if (target != null && check is MoveCheck.Allowed) {
                                                        drag.clear()
                                                        move(request, target)
                                                    } else {
                                                        if (check is MoveCheck.Refused) refusal = check.reason
                                                        scope.launch { drag.goBack() }
                                                    }
                                                })
                                        }
                                    }
                                    // The ticket being dragged, under the pointer, over everything.
                                    drag.request?.let { dragged ->
                                        val density = androidx.compose.ui.platform.LocalDensity.current
                                        val target = drag.over
                                        val blocked = target != null && rules.check(dragged, target) is MoveCheck.Refused
                                        Box(
                                            Modifier.offset {
                                                androidx.compose.ui.unit.IntOffset((drag.pointer.x - drag.grab.x - origin.x).toInt(), (drag.pointer.y - drag.grab.y - origin.y).toInt())
                                            }.width(with(density) { drag.size.width.toDp() }).zIndex(10f)
                                                .graphicsLayer { scaleX = 1.03f; scaleY = 1.03f }
                                        ) {
                                            TicketCard(dragged, rules.next(dragged), floating = if (blocked) FloatLook.BLOCKED else FloatLook.OK)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // A card opens big with everything about it and every step (user, 2026-11-21).
        list?.firstOrNull { it.id == openId }?.let { opened ->
            RequestDialog(opened, rules, busy, dialogError.takeIf { handling == null },
                onClose = { openId = null },
                onAssign = { dialogError = null; handling = opened to HandleWay.ASSIGN },
                onStart = { move(opened, RequestStage.IN_PROGRESS) },
                onHandle = { move(opened, RequestStage.SOLVED) },
                onTakeBack = { move(opened, RequestStage.OPEN) })
        }
        handling?.let { (request, way) ->
            HandleRequestDialog(request, admins, self, superAdmin, canAssign, busy, dialogError, way,
                onDismiss = { if (!busy) handling = null },
                onRemove = { note -> answer(request, true, note, null, "${request.targetName ?: "The owner"} was removed") },
                onSolve = { note -> act(request, "Marked as solved", null) { platform.solveRequest(request.id, note) } },
                onAssign = { admin, note, give ->
                    act(request, when {
                        admin == null -> "Taken back"
                        admin.id == me -> "It's yours"
                        give && request.kind == "NEED_ADMIN" -> "${admin.name} takes care of ${request.restaurantName ?: "the restaurant"} now"
                        else -> "Given to ${admin.name}"
                    }, null) { platform.assignRequest(request.id, admin?.id, note, give) }
                })
        }
        historyOf?.let { request -> RequestHistoryDialog(request, platform) { historyOf = null } }
        if (newTicket) {
            NewTicketDialog(platform, onClose = { newTicket = false }) { created ->
                newTicket = false
                // The new ticket shows at once in Open, its frame lit for a moment.
                list = listOf(created) + list.orEmpty().filter { it.id != created.id }
                phoneStage = RequestStage.OPEN
                flashId = created.id
                flashTick++
                reload++
            }
        }
        // A move the rules don't allow: a yellow note bottom-left, and the card goes back.
        com.saporini.mobile_desktop.core.components.BottomToast(refusal, isWarning = true) { refusal = null }
    }
}

private fun isRemoval(request: PlatformRequestDto) = request.kind == "REMOVE_OWNER"

/**
 * The board's bar, like the kitchen board's (user, 2026-11-21: "make the ticket more like the kitchen status"): a plain back
 * arrow, "Requests" and how many are open, then the search, one Filters button and the reload. On a phone the search and the
 * Filters go on a second line.
 */
@Composable
private fun BoardBar(
    superAdmin: Boolean, narrow: Boolean, open: Int?, loading: Boolean, query: String, onQuery: (String) -> Unit,
    filters: @Composable (androidx.compose.ui.unit.Dp) -> Unit, onReload: () -> Unit, onBack: () -> Unit, onNew: (() -> Unit)? = null
) {
    val title: @Composable (Modifier) -> Unit = { m ->
        Row(m, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.saporini.mobile_desktop.core.ui.HoverTooltip("Back to the restaurants") {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to the restaurants",
                    Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onBack).padding(5.dp), tint = Kit.Ink)
            }
            Column(Modifier.weight(1f, fill = false)) {
                Text("Requests", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (narrow) 19.sp else 22.sp, color = Kit.Ink)
                Text(
                    when {
                        open == null -> if (superAdmin) "What restaurants ask you" else "What the Super Admin gave you"
                        open == 1 -> "1 still open"
                        else -> "$open still open"
                    },
                    fontFamily = Inter(), fontSize = 12.5.sp, color = Kit.Muted, maxLines = 1
                )
            }
        }
    }
    if (narrow) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                title(Modifier.weight(1f))
                RefreshButton(loading, onReload, size = 40.dp)
                if (onNew != null) {
                    Spacer(Modifier.width(8.dp))
                    KitButton("New ticket", onNew, icon = Icons.Outlined.Add)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                com.saporini.mobile_desktop.core.components.SearchField(query, onQuery, Modifier.weight(1f), placeholder = "Search requests", height = 40.dp)
                filters(40.dp)
            }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            title(Modifier.weight(1f))
            com.saporini.mobile_desktop.core.components.SearchField(query, onQuery, Modifier.width(320.dp),
                placeholder = "Search restaurant, person or message", height = 40.dp)
            filters(40.dp)
            RefreshButton(loading, onReload, size = 40.dp)
            if (onNew != null) KitButton("New ticket", onNew, icon = Icons.Outlined.Add)
        }
    }
}

/**
 * A ticket the Super Admin writes themselves (user, 2026-11-21: "make here that I can open a ticket myself"), e.g. after a
 * restaurant called: which restaurant and what's wrong. It lands in Open, to be given out or taken like any other.
 */
@Composable
private fun NewTicketDialog(platform: PlatformRepository, onClose: () -> Unit, onCreated: (PlatformRequestDto) -> Unit) {
    val scope = rememberCoroutineScope()
    var restaurants by remember { mutableStateOf<List<com.saporini.mobile_desktop.platform.RestaurantRefDto>?>(null) }
    var restaurant by remember { mutableStateOf<com.saporini.mobile_desktop.platform.RestaurantRefDto?>(null) }
    var message by remember { mutableStateOf("") }
    var showProblems by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            restaurants = platform.cards("", null, 0, 100).items.map { com.saporini.mobile_desktop.platform.RestaurantRefDto(it.id, it.name) }
                .sortedBy { it.name.lowercase() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            restaurants = emptyList()
            error = platformMessage(e, write = false)
        }
    }
    val send: () -> Unit = {
        val chosen = restaurant
        if (chosen == null || message.isBlank()) showProblems = true
        else {
            sending = true
            error = null
            scope.launch {
                try {
                    onCreated(platform.openTicket(chosen.id, message.trim()))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = platformMessage(e, write = true)
                } finally {
                    sending = false
                }
            }
        }
        Unit
    }
    AppDialog(
        "New ticket", onClose,
        subtitle = "For a restaurant, e.g. after they called. It starts in Open.",
        busy = sending, maxWidth = 560.dp,
        buttons = {
            KitButton("Cancel", onClose, style = ButtonStyle.SECONDARY, enabled = !sending)
            KitButton("Open ticket", send, icon = Icons.Outlined.Add, loading = sending, enabled = restaurants != null)
        }
    ) {
        error?.let { MessageBar(it, MessageKind.ERROR) }
        com.saporini.mobile_desktop.core.components.SearchableSelect(
            restaurant, restaurants.orEmpty(), { it.name }, { restaurant = it }, Modifier.fillMaxWidth(),
            label = "Restaurant", icon = Icons.Outlined.Storefront, required = true, enabled = restaurants != null,
            placeholder = if (restaurants == null) "Loading the restaurants…" else "Choose a restaurant",
            problem = if (showProblems && restaurant == null) "Choose the restaurant it's about." else null
        )
        TextInput(message, { message = it.take(2000); showProblems = false }, Modifier.fillMaxWidth(), label = "What's wrong?", required = true,
            singleLine = false, minLines = 5, maxLength = 2000,
            placeholder = "What happened, where in the app, and since when",
            problem = if (showProblems && message.isBlank()) "Write what the problem is." else null)
    }
}

/** One Filters button (the app's one-menu rule): what the request is about, each kind with how many. */
@Composable
private fun KindFilterButton(kind: String?, all: List<PlatformRequestDto>, size: androidx.compose.ui.unit.Dp, onKind: (String?) -> Unit) {
    com.saporini.mobile_desktop.admin.inventory.ui.PhoneOptionsButton(Icons.Outlined.FilterList, "Filters", if (kind != null) 1 else 0, size) { close ->
        com.saporini.mobile_desktop.admin.inventory.ui.OptionGroup("What it's about")
        com.saporini.mobile_desktop.admin.inventory.ui.OptionLine("Everything", kind == null, { onKind(null); close() },
            icon = Icons.Outlined.MarkEmailUnread, count = all.size)
        listOf("PROBLEM", "REMOVE_OWNER", "NEED_ADMIN").forEach { option ->
            val look = kindLook(option)
            com.saporini.mobile_desktop.admin.inventory.ui.OptionLine(look.label, kind == option, { onKind(option); close() },
                icon = look.icon, color = look.color, count = all.count { it.kind == option })
        }
    }
}

/** Open ones oldest first (waiting longest on top), assigned and in progress by when that happened, solved newest first. */
private fun sortedFor(stage: RequestStage, requests: List<PlatformRequestDto>): List<PlatformRequestDto> = when (stage) {
    RequestStage.OPEN -> requests.sortedBy { it.createdAt.orEmpty() }
    RequestStage.ASSIGNED -> requests.sortedBy { it.assignedAt ?: it.createdAt.orEmpty() }
    RequestStage.IN_PROGRESS -> requests.sortedBy { it.startedAt ?: it.createdAt.orEmpty() }
    RequestStage.SOLVED -> requests.sortedByDescending { it.decidedAt ?: it.createdAt.orEmpty() }
}

/** What a move does: give it to someone (the picker opens), start it (it's yours if nobody had it), solve it, take it back. */
private enum class MoveKind { ASSIGN, START, SOLVE, TAKE_BACK }

private sealed class MoveCheck {
    data object Same : MoveCheck()
    data class Allowed(val kind: MoveKind) : MoveCheck()
    data class Refused(val reason: String) : MoveCheck()
}

/**
 * Where a ticket may go (user, 2026-11-21: "drag and drop based on rules, not anywhere"): one step on, Open → Assigned (give
 * it to someone) or straight to In progress (take it yourself), Assigned → In progress, In progress → Solved; back to Open
 * takes it back. Giving out and taking back are for the Super Admin and support admins with "Assign requests"; starting and
 * solving for the one who has it (or the Super Admin). Solved stays solved.
 */
private class BoardRules(val superAdmin: Boolean, val canAssign: Boolean, val me: String?) {
    /** The Super Admin, or the one it's given to. */
    fun works(request: PlatformRequestDto) = superAdmin || (me != null && request.assignedToId == me)

    fun check(request: PlatformRequestDto, to: RequestStage): MoveCheck {
        val from = stageOf(request)
        val who = request.assignedToName ?: "the one who has it"
        return when {
            to == from -> MoveCheck.Same
            from == RequestStage.SOLVED -> MoveCheck.Refused("A solved request stays solved.")
            to == RequestStage.OPEN -> if (canAssign) MoveCheck.Allowed(MoveKind.TAKE_BACK)
                else MoveCheck.Refused("Only the Super Admin takes requests back.")
            to == RequestStage.ASSIGNED -> when {
                from != RequestStage.OPEN -> MoveCheck.Refused("It's already being worked on. Move it back to Open to give it to someone else.")
                canAssign -> MoveCheck.Allowed(MoveKind.ASSIGN)
                else -> MoveCheck.Refused("Only the Super Admin gives requests to others.")
            }
            to == RequestStage.IN_PROGRESS -> when {
                from == RequestStage.OPEN && canAssign -> MoveCheck.Allowed(MoveKind.START)
                from == RequestStage.ASSIGNED && works(request) -> MoveCheck.Allowed(MoveKind.START)
                else -> MoveCheck.Refused("Only $who can start it.")
            }
            else -> when {
                from != RequestStage.IN_PROGRESS -> MoveCheck.Refused("Start it before you solve it.")
                works(request) -> MoveCheck.Allowed(MoveKind.SOLVE)
                else -> MoveCheck.Refused("Only $who can solve it.")
            }
        }
    }

    /** Where the card's button takes it, when this person may: Assign, Start, then Solve (or Handle). */
    fun next(request: PlatformRequestDto): RequestStage? = when (stageOf(request)) {
        RequestStage.OPEN -> RequestStage.ASSIGNED.takeIf { canAssign }
        RequestStage.ASSIGNED -> RequestStage.IN_PROGRESS.takeIf { works(request) }
        RequestStage.IN_PROGRESS -> RequestStage.SOLVED.takeIf { works(request) }
        RequestStage.SOLVED -> null
    }

    /** A ticket this person can move somewhere. */
    fun mayDrag(request: PlatformRequestDto) = RequestStage.entries.any { check(request, it) is MoveCheck.Allowed }
}

/** How a column looks while a ticket is dragged: where it may go (green), where it is now (green, solid), where it may not. */
private enum class DropLook { NONE, OK, OVER_OK, NO, OVER_NO }

/** The dragged ticket's own look: fine where it is, or "You can't put it here". */
private enum class FloatLook { OK, BLOCKED }

/** A ticket being dragged across the board (window coordinates), and where each column is. */
private class BoardDrag {
    var request by mutableStateOf<PlatformRequestDto?>(null)
    var from by mutableStateOf(RequestStage.OPEN)
    var pointer by mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
    var start by mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
    var grab by mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
    var size by mutableStateOf(androidx.compose.ui.unit.IntSize.Zero)
    var returning by mutableStateOf(false)
    val columns = androidx.compose.runtime.mutableStateMapOf<RequestStage, androidx.compose.ui.geometry.Rect>()

    /** The column under the pointer. */
    val over: RequestStage?
        get() = if (request == null || returning) null else columns.entries.firstOrNull { it.value.contains(pointer) }?.key

    fun begin(dragged: PlatformRequestDto, stage: RequestStage, at: androidx.compose.ui.geometry.Offset, grabbed: androidx.compose.ui.geometry.Offset,
              cardSize: androidx.compose.ui.unit.IntSize) {
        request = dragged; from = stage; pointer = at; start = at; grab = grabbed; size = cardSize; returning = false
    }

    fun clear() { request = null; returning = false }

    /** Dropped where it can't go: it glides back to its place. */
    suspend fun goBack() {
        if (request == null) return
        returning = true
        val anim = androidx.compose.animation.core.Animatable(pointer, androidx.compose.ui.geometry.Offset.VectorConverter)
        anim.animateTo(start, tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { pointer = value }
        clear()
    }

    fun lookOf(stage: RequestStage, rules: BoardRules): DropLook {
        val dragged = request ?: return DropLook.NONE
        if (returning || stage == from) return DropLook.NONE
        val ok = rules.check(dragged, stage) is MoveCheck.Allowed
        val on = over == stage
        return when {
            ok && on -> DropLook.OVER_OK
            ok -> DropLook.OK
            on -> DropLook.OVER_NO
            else -> DropLook.NO
        }
    }
}

/**
 * A column of the board: a thin bar of the stage's colour on top, its name with a dot and the count, and its tickets on a
 * quiet grey (user, 2026-11-21: "too messy, too much text": colour only where it says something). While a ticket is dragged
 * a column it may go to gets a green frame (solid under the pointer), and one it may not go to looks switched off.
 */
@Composable
private fun BoardColumn(
    stage: RequestStage, requests: List<PlatformRequestDto>, rules: BoardRules, modifier: Modifier, showHead: Boolean = true,
    flashId: String? = null, drag: BoardDrag? = null, look: DropLook = DropLook.NONE,
    onOpen: (PlatformRequestDto) -> Unit, onStep: (PlatformRequestDto) -> Unit, onHistory: (PlatformRequestDto) -> Unit = {},
    onDrop: (PlatformRequestDto) -> Unit = {}
) {
    val frame by androidx.compose.animation.animateColorAsState(when (look) {
        DropLook.OVER_OK -> Kit.Green
        DropLook.OK -> Kit.Green.copy(alpha = 0.45f)
        DropLook.OVER_NO -> Kit.Amber
        else -> Kit.Border
    }, label = "column-frame")
    val fill by androidx.compose.animation.animateColorAsState(if (look == DropLook.OVER_OK) Color(0xFFEAF3E6) else Color(0xFFF4F5F2), label = "column-fill")
    val fade by androidx.compose.animation.core.animateFloatAsState(if (look == DropLook.NO || look == DropLook.OVER_NO) 0.45f else 1f, label = "column-fade")
    Surface(modifier.graphicsLayer { alpha = fade }, shape = RoundedCornerShape(16.dp), color = fill,
        border = BorderStroke(if (look == DropLook.OVER_OK || look == DropLook.OVER_NO) 2.dp else if (look == DropLook.OK) 1.5.dp else 1.dp, frame)) {
        Column {
            if (showHead) {
                Box(Modifier.fillMaxWidth().height(3.dp).background(stage.color))
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(stage.color))
                    Text(stage.label, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink, maxLines = 1)
                    Text("${requests.size}", Modifier.clip(RoundedCornerShape(50)).background(stage.color.copy(alpha = 0.12f))
                        .padding(horizontal = 9.dp, vertical = 2.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        color = stage.color, translate = false)
                }
            }
            if (requests.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f).padding(20.dp), Alignment.TopCenter) {
                    Text(stage.empty, Modifier.padding(top = 24.dp), fontFamily = Inter(), fontSize = 13.sp, color = Kit.Faint)
                }
            } else {
                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                // The card that just came here comes into view.
                LaunchedEffect(flashId, requests.size) {
                    val index = requests.indexOfFirst { it.id == flashId }
                    if (index >= 0) listState.animateScrollToItem(index)
                }
                com.saporini.mobile_desktop.core.components.ScrollList(
                    Modifier.fillMaxWidth().weight(1f),
                    state = listState,
                    contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(requests, key = { it.id }) { request ->
                        Box(Modifier.animateItem(fadeInSpec = tween(220), placementSpec = tween(320), fadeOutSpec = tween(160))) {
                            val scope = rememberCoroutineScope()
                            val current by androidx.compose.runtime.rememberUpdatedState(request)
                            val dropped by androidx.compose.runtime.rememberUpdatedState(onDrop)
                            var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                            var cardSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
                            val draggable = drag != null && rules.mayDrag(request)
                            val handle = if (drag != null && draggable) {
                                Modifier.onGloballyPositioned { origin = it.positionInRoot(); cardSize = it.size }
                                    .pointerInput(request.id) {
                                        detectDragGestures(
                                            onDragStart = { at -> drag.begin(current, stage, origin + at, at, cardSize) },
                                            onDrag = { change, amount -> change.consume(); drag.pointer += amount },
                                            onDragEnd = { dropped(current) },
                                            onDragCancel = { scope.launch { drag.goBack() } }
                                        )
                                    }
                            } else Modifier
                            TicketCard(request, rules.next(request), dragged = drag?.request?.id == request.id, flash = request.id == flashId, modifier = handle,
                                onOpen = { onOpen(request) }, onStep = { onStep(request) }, onHistory = { onHistory(request) })
                        }
                    }
                }
            }
        }
    }
}

/**
 * One request as a small ticket (user, 2026-11-21: "the cards, make them much better"): the restaurant and when, who sent it,
 * what it's about in two lines at most, then the kind, who has it (a face) and the next step as a small button. Everything else
 * is in the big window a click opens. It can be dragged to another column, by the board's rules.
 */
@Composable
private fun TicketCard(
    request: PlatformRequestDto, next: RequestStage?, floating: FloatLook? = null, dragged: Boolean = false, flash: Boolean = false,
    modifier: Modifier = Modifier, onOpen: () -> Unit = {}, onStep: () -> Unit = {}, onHistory: (() -> Unit)? = null
) {
    // Just moved here: a green frame for a few seconds, then back to the usual line.
    val flashFrame by androidx.compose.animation.animateColorAsState(if (flash) Kit.Green else Color(0xFFE6E9E3), tween(if (flash) 200 else 900),
        label = "ticket-flash")
    val zone = remember { TimeZone.currentSystemDefault() }
    val now = remember { Clock.System.now() }
    val stage = stageOf(request)
    val name = request.restaurantName ?: "A restaurant"
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val lift by animateDpAsState(if (floating != null) 18.dp else if (hovered) 6.dp else 0.dp, label = "ticket-lift")
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.graphicsLayer { alpha = if (dragged) 0.25f else 1f }) {
        Surface(
            onClick = onOpen, interactionSource = interaction,
            modifier = Modifier.fillMaxWidth().shadow(lift, shape, ambientColor = Color(0x14000000), spotColor = Color(0x22000000)),
            shape = shape, color = Color.White,
            border = when (floating) {
                FloatLook.BLOCKED -> BorderStroke(2.dp, Kit.Amber)
                FloatLook.OK -> BorderStroke(1.5.dp, Kit.Green)
                null -> if (flash) BorderStroke(2.dp, flashFrame) else BorderStroke(1.dp, if (hovered) Kit.Green.copy(alpha = 0.35f) else flashFrame)
            }
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    RestaurantLogo(request.restaurantLogoUrl, name, 30.dp)
                    Column(Modifier.weight(1f)) {
                        Text(name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = Kit.Ink, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, translate = false)
                        Text(request.createdByName ?: "Someone there", fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Muted, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, translate = request.createdByName == null)
                    }
                    Text(agoText(if (stage == RequestStage.SOLVED) request.decidedAt ?: request.createdAt else request.createdAt, now, zone),
                        fontFamily = Inter(), fontSize = 11.sp, color = Kit.Faint, maxLines = 1)
                }
                // The message in grey, two lines; a long one opens on the card with "See more" (user, 2026-11-21).
                var expanded by remember(request.id) { mutableStateOf(false) }
                var long by remember(request.id) { mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (isRemoval(request)) "Remove ${request.targetName ?: "an owner"}" else request.note ?: "No message.",
                        fontFamily = Inter(), fontSize = 12.5.sp, lineHeight = 17.sp, color = Kit.Muted,
                        maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis,
                        translate = isRemoval(request) || request.note == null,
                        onTextLayout = { if (!expanded) long = it.hasVisualOverflow }
                    )
                    if (long && floating == null) {
                        Text(if (expanded) "See less" else "See more",
                            Modifier.clip(RoundedCornerShape(6.dp)).clickable { expanded = !expanded }.padding(vertical = 2.dp),
                            fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Green)
                    }
                }
                // A thin line between the message and the foot, fading out at both ends (user, 2026-11-21).
                Box(Modifier.fillMaxWidth().height(1.dp).background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        0f to Color(0x00DDE2D9), 0.18f to Color(0xFFDDE2D9), 0.82f to Color(0xFFDDE2D9), 1f to Color(0x00DDE2D9)
                    )
                ))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // The kind gives way when the row is tight, so the step button is never cut.
                    Box(Modifier.weight(1f)) { KindTag(request.kind) }
                    // Everything that happened to it (user, 2026-11-21: "each card has a history icon").
                    if (onHistory != null && floating == null) {
                        com.saporini.mobile_desktop.core.ui.HoverTooltip("History") {
                            Box(Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onHistory), Alignment.Center) {
                                Icon(Icons.Outlined.History, "History", Modifier.size(17.dp), tint = Kit.Muted)
                            }
                        }
                    }
                    if (stage == RequestStage.SOLVED) {
                        Icon(Icons.Outlined.TaskAlt, "Solved", Modifier.size(17.dp), tint = Kit.Green)
                    } else {
                        // Who has it: their face, or an empty circle.
                        val who = request.assignedToName ?: request.startedByName
                        com.saporini.mobile_desktop.core.ui.HoverTooltip(who ?: "Nobody has it yet") {
                            if (who != null) InitialsAvatar(who, color = Kit.Purple, size = 26.dp)
                            else Box(Modifier.size(26.dp).clip(CircleShape).border(1.dp, Kit.Border, CircleShape), Alignment.Center) {
                                Icon(Icons.Outlined.PersonOutline, null, Modifier.size(14.dp), tint = Kit.Faint)
                            }
                        }
                        when (next) {
                            RequestStage.ASSIGNED -> StepChip("Assign", Icons.Outlined.AssignmentInd, onStep)
                            RequestStage.IN_PROGRESS -> StepChip("Start", Icons.Outlined.PlayArrow, onStep)
                            RequestStage.SOLVED -> StepChip(if (isRemoval(request)) "Handle" else "Solve", Icons.Outlined.Check, onStep)
                            else -> {}
                        }
                    }
                }
            }
        }
        if (floating == FloatLook.BLOCKED) {
            Row(
                Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-10).dp).clip(RoundedCornerShape(50)).background(Kit.Amber)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(Icons.Outlined.Block, null, Modifier.size(13.dp), tint = Color.White)
                Text("You can't put it here", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White, maxLines = 1)
            }
        }
    }
}

/** What a request is about, small: an icon and one word or two in its colour. */
@Composable
private fun KindTag(kind: String) {
    val look = kindLook(kind)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(look.icon, null, Modifier.size(14.dp), tint = look.color)
        Text(look.short, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = look.color, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

/** The ticket's next step as a small green button. */
@Composable
private fun StepChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = Kit.Green) {
        Row(Modifier.padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, null, Modifier.size(15.dp), tint = Color.White)
            Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Color.White, maxLines = 1)
        }
    }
}

/** The board while it loads: the four columns and ticket shapes in them (one column on a phone). */
@Composable
private fun BoardShape(narrow: Boolean, modifier: Modifier) {
    val alpha = com.saporini.mobile_desktop.core.components.rememberSkeletonAlpha("requests-board")
    val block = com.saporini.mobile_desktop.core.components.SkeletonBlock
    val light = com.saporini.mobile_desktop.core.components.SkeletonLight
    Row(modifier.alpha(alpha), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(if (narrow) 1 else 4) { column ->
            Surface(Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(16.dp), color = Color(0xFFF4F5F2), border = BorderStroke(1.dp, Kit.Border)) {
                Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 2.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        com.saporini.mobile_desktop.core.components.SkeletonBox(Modifier.size(width = 90.dp, height = 13.dp), block)
                        Spacer(Modifier.weight(1f))
                        com.saporini.mobile_desktop.core.components.SkeletonBox(Modifier.size(width = 26.dp, height = 18.dp), light, RoundedCornerShape(50))
                    }
                    repeat(if (column == 0) 3 else 2) {
                        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFE6E9E3))) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                    com.saporini.mobile_desktop.core.components.SkeletonBox(Modifier.size(30.dp), light, RoundedCornerShape(8.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        com.saporini.mobile_desktop.core.components.SkeletonBox(Modifier.fillMaxWidth(0.6f).height(11.dp), block)
                                        com.saporini.mobile_desktop.core.components.SkeletonBox(Modifier.fillMaxWidth(0.4f).height(8.dp), light)
                                    }
                                }
                                com.saporini.mobile_desktop.core.components.SkeletonBox(Modifier.fillMaxWidth(0.95f).height(9.dp), light)
                                com.saporini.mobile_desktop.core.components.SkeletonBox(Modifier.fillMaxWidth(0.65f).height(9.dp), light)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    com.saporini.mobile_desktop.core.components.SkeletonBox(Modifier.size(width = 64.dp, height = 10.dp), light)
                                    Spacer(Modifier.weight(1f))
                                    com.saporini.mobile_desktop.core.components.SkeletonBox(Modifier.size(width = 74.dp, height = 28.dp), light, RoundedCornerShape(50))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One request, big: the restaurant on its picture, where it stands (Open → Assigned → In progress → Solved), the whole
 * message, who asked and how to reach them (and the owner to remove), who has it, how it was solved, and every step it can
 * take next: Assign (the Super Admin), Start, Handle / Solve.
 */
@Composable
private fun RequestDialog(
    request: PlatformRequestDto, rules: BoardRules, busy: Boolean, error: String?,
    onClose: () -> Unit, onAssign: () -> Unit, onStart: () -> Unit, onHandle: () -> Unit, onTakeBack: () -> Unit
) {
    val zone = remember { TimeZone.currentSystemDefault() }
    val now = remember { Clock.System.now() }
    val stage = stageOf(request)
    val look = kindLook(request.kind)
    val name = request.restaurantName ?: "A restaurant"
    val open = stage != RequestStage.SOLVED
    AppDialog(
        look.label, onClose,
        subtitle = listOfNotNull(request.createdByName?.let { "Asked by $it" }, request.createdAt?.let { dateTimeText(it, zone) }).joinToString(" · "),
        busy = busy, maxWidth = 860.dp,
        buttons = {
            KitButton("Close", onClose, style = ButtonStyle.SECONDARY, enabled = !busy)
            Box(Modifier.weight(1f))
            // The same steps as on the board, by the same rules.
            if (rules.check(request, RequestStage.OPEN) is MoveCheck.Allowed) KitButton("Back to open", onTakeBack, style = ButtonStyle.SECONDARY,
                icon = Icons.AutoMirrored.Outlined.Undo, enabled = !busy)
            if (open && stage != RequestStage.IN_PROGRESS && rules.canAssign) KitButton(if (request.assignedToId == null) "Assign" else "Reassign", onAssign,
                style = ButtonStyle.SECONDARY, icon = Icons.Outlined.AssignmentInd, enabled = !busy)
            if (rules.check(request, RequestStage.IN_PROGRESS) is MoveCheck.Allowed) KitButton(if (request.assignedToId == null) "Take it and start" else "Start",
                onStart, style = ButtonStyle.SECONDARY, icon = Icons.Outlined.PlayArrow, loading = busy)
            if (rules.check(request, RequestStage.SOLVED) is MoveCheck.Allowed) KitButton(if (isRemoval(request)) "Handle" else "Solve", onHandle,
                icon = Icons.Outlined.TaskAlt, enabled = !busy)
        }
    ) {
        error?.let { MessageBar(it, MessageKind.ERROR) }
        // The restaurant on its own picture, with the kind and where it stands.
        Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(16.dp))) {
            CoverBackground(request.restaurantCoverUrl, accentOf(name))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))))
            Row(Modifier.align(Alignment.TopStart).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White)) { KindPill(request.kind) }
                StagePill(stage)
            }
            Row(Modifier.align(Alignment.BottomStart).padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(2.dp)) {
                    RestaurantLogo(request.restaurantLogoUrl, name, 44.dp)
                }
                Text(name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, translate = false)
            }
        }
        ProgressSteps(request)
        if (isRemoval(request)) {
            Text("Remove ${request.targetName ?: "an owner"} from $name", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Danger)
        }
        Quote(request.note ?: "No message.", maxLines = 40)
        val narrowDialog = com.saporini.mobile_desktop.core.components.LocalNarrowDialog.current
        val people: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PersonContact(request.createdByName ?: "Someone there", "Asked", request.createdByEmail, request.createdByPhone, Kit.Green)
                if (isRemoval(request)) PersonContact(request.targetName ?: "The owner", "Owner to remove", request.targetEmail, request.targetPhone, Kit.Danger)
            }
        }
        val handling: @Composable (Modifier) -> Unit = { m ->
            Column(
                m.clip(RoundedCornerShape(12.dp)).border(1.dp, Kit.Border, RoundedCornerShape(12.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("WHO HAS IT", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp, color = Kit.Faint)
                val who = request.assignedToName
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (who != null) InitialsAvatar(who, color = Kit.Purple, size = 34.dp)
                    Column(Modifier.weight(1f)) {
                        Text(who ?: if (rules.canAssign) "Nobody yet: assign it, or start it yourself" else "Nobody yet", fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp, color = if (who == null) Kit.Muted else Kit.Ink, maxLines = 2, translate = who == null)
                        request.assignedAt?.let { Text("Assigned ${agoText(it, now, zone)}", fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Muted) }
                    }
                }
                request.assignNote?.let { Text("“$it”", fontFamily = Inter(), fontSize = 12.5.sp, lineHeight = 18.sp, color = Kit.Ink, translate = false) }
                request.startedAt?.let {
                    Text(listOfNotNull("Started ${agoText(it, now, zone)}", request.startedByName?.let { by -> "by $by" }).joinToString(" "),
                        fontFamily = Inter(), fontSize = 12.sp, color = Kit.Blue)
                }
                if (!open) {
                    KitDivider()
                    Text("HOW IT WAS SOLVED", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp, color = Kit.Faint)
                    Text(request.decisionNote ?: "Solved, without a note.", fontFamily = Inter(), fontSize = 13.sp, lineHeight = 19.sp, color = Kit.Ink,
                        translate = request.decisionNote == null)
                    Text(listOfNotNull(request.decidedByName?.let { "By $it" }, request.decidedAt?.let { dateTimeText(it, zone) }).joinToString(" · "),
                        fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Muted)
                }
            }
        }
        if (narrowDialog) {
            people(Modifier.fillMaxWidth())
            handling(Modifier.fillMaxWidth())
        } else {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                people(Modifier.weight(1f))
                handling(Modifier.weight(1f).fillMaxHeight())
            }
        }
        Text("HISTORY", Modifier.padding(top = 4.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp, color = Kit.Faint)
        RequestTimeline(request, koinInject())
    }
}

/** A request's history in a window of its own (the card's clock button). */
@Composable
private fun RequestHistoryDialog(request: PlatformRequestDto, platform: PlatformRepository, onClose: () -> Unit) {
    AppDialog("History", onClose, subtitle = listOfNotNull(request.restaurantName, kindLook(request.kind).label).joinToString(" · "), maxWidth = 520.dp,
        buttons = { Box(Modifier.weight(1f)); KitButton("Close", onClose, style = ButtonStyle.SECONDARY) }) {
        RequestTimeline(request, platform)
    }
}

/** How one thing in a request's history looks: its icon, colour and words. */
private fun eventLine(event: RequestEventDto): Triple<androidx.compose.ui.graphics.vector.ImageVector, Color, String> {
    val by = event.byName ?: "Someone"
    val to = event.toName
    return when (event.kind) {
        "CREATED" -> Triple(Icons.Outlined.MarkEmailUnread, Kit.Amber, "Sent by $by")
        "ASSIGNED" -> Triple(Icons.Outlined.AssignmentInd, Kit.Purple, if (to != null) "$by gave it to $to" else "$by gave it to someone")
        "TAKEN" -> Triple(Icons.Outlined.AssignmentInd, Kit.Purple, "$by took it")
        "TAKEN_BACK" -> Triple(Icons.AutoMirrored.Outlined.Undo, Kit.Amber, if (to != null) "$by took it back from $to" else "$by took it back")
        "STARTED" -> Triple(Icons.Outlined.PlayArrow, Kit.Blue, "$by started it")
        "SOLVED" -> Triple(Icons.Outlined.TaskAlt, Kit.Green, "$by solved it")
        "APPROVED" -> Triple(Icons.Outlined.TaskAlt, Kit.Green, "$by solved it")
        "OWNER_REMOVED" -> Triple(Icons.Outlined.PersonRemove, Kit.Danger, if (to != null) "$by removed $to" else "$by removed the owner")
        "DECLINED" -> Triple(Icons.Outlined.Close, Kit.Grey, "$by declined it")
        "RESTAURANT_GIVEN" -> Triple(Icons.Outlined.Storefront, Kit.Blue, if (to != null) "$by gave $to the restaurant" else "$by gave the restaurant to a support admin")
        else -> Triple(Icons.Outlined.History, Kit.Grey, by)
    }
}

/**
 * Everything that happened to a request, oldest first, as a line of steps: an icon in its colour, who did what (to whom),
 * when, and the note they left. Loads again when the request changes.
 */
@Composable
private fun RequestTimeline(request: PlatformRequestDto, platform: PlatformRepository) {
    val zone = remember { TimeZone.currentSystemDefault() }
    val now = remember { Clock.System.now() }
    var events by remember(request.id) { mutableStateOf<List<RequestEventDto>?>(null) }
    var failed by remember(request.id) { mutableStateOf(false) }
    LaunchedEffect(request.id, request.status, request.assignedToId, request.startedAt) {
        try {
            events = platform.requestHistory(request.id)
            failed = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
        }
    }
    val list = events
    when {
        list == null && failed -> Text("The history couldn't be loaded.", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted)
        list == null -> SwitchLinesShape(3)
        list.isEmpty() -> Text("Nothing written down yet.", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted)
        else -> Column {
            list.forEachIndexed { index, event ->
                val (icon, color, words) = eventLine(event)
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // The step's circle, and the line down to the next one.
                    Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(30.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)), Alignment.Center) {
                            Icon(icon, null, Modifier.size(16.dp), tint = color)
                        }
                        if (index < list.lastIndex) Box(Modifier.width(2.dp).weight(1f).background(Kit.Border))
                    }
                    Column(Modifier.weight(1f).padding(top = 5.dp, bottom = if (index < list.lastIndex) 16.dp else 0.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(words, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = Kit.Ink)
                        Text(listOfNotNull(event.at?.let { dateTimeText(it, zone) }, event.at?.let { agoText(it, now, zone) }).joinToString(" · "),
                            fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Muted)
                        event.note?.takeIf { it.isNotBlank() }?.let {
                            Text("“$it”", Modifier.padding(top = 2.dp).clip(RoundedCornerShape(8.dp)).background(Kit.Canvas).padding(horizontal = 10.dp, vertical = 6.dp),
                                fontFamily = Inter(), fontSize = 12.5.sp, lineHeight = 18.sp, color = Kit.Ink, translate = false)
                        }
                    }
                }
            }
        }
    }
}

/** What a request is about: its words, icon and colour. */
private data class KindLook(val label: String, val short: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: Color)

private fun kindLook(kind: String) = when (kind) {
    "REMOVE_OWNER" -> KindLook("Remove an owner", "Owner removal", Icons.Outlined.PersonRemove, Kit.Danger)
    "NEED_ADMIN" -> KindLook("Needs a support admin", "Support admin", Icons.Outlined.SupportAgent, Kit.Blue)
    else -> KindLook("Problem", "Problem", Icons.Outlined.ReportProblem, ProblemColor)
}

private val ProblemColor = Color(0xFFC2255C)

@Composable
private fun KindPill(kind: String) {
    val look = kindLook(kind)
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(look.color.copy(alpha = 0.09f)).padding(start = 7.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(look.icon, null, Modifier.size(13.dp), tint = look.color)
        Text(look.label, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = look.color, maxLines = 1)
    }
}

/** What the owner wrote, as a quote. */
@Composable
private fun Quote(text: String, maxLines: Int = 3) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Canvas).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Outlined.FormatQuote, null, Modifier.size(18.dp), tint = Kit.Faint)
        Text(text, fontFamily = Inter(), fontSize = if (maxLines > 3) 14.sp else 13.sp, lineHeight = if (maxLines > 3) 21.sp else 19.sp, color = Kit.Ink,
            maxLines = maxLines, overflow = TextOverflow.Ellipsis, translate = false)
    }
}

internal enum class HandleWay { REMOVE, ASSIGN, SOLVED }

/** Where a request stands (user, 2026-11-21: "show the progress of the ticket: open, in progress, assigned…"). */
private enum class RequestStage(val label: String, val color: Color, val icon: androidx.compose.ui.graphics.vector.ImageVector, val empty: String) {
    OPEN("Open", Kit.Amber, Icons.Outlined.MarkEmailUnread, "Nothing new"),
    ASSIGNED("Assigned", Kit.Purple, Icons.Outlined.AssignmentInd, "Nothing assigned"),
    IN_PROGRESS("In progress", Kit.Blue, Icons.Outlined.PlayArrow, "Nothing in progress"),
    SOLVED("Solved", Kit.Green, Icons.Outlined.TaskAlt, "Nothing solved yet")
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
    // You, first among who can take it (user, 2026-11-21: "I can do it myself, I don't have to assign it to someone").
    self: AdminCardDto?,
    superAdmin: Boolean,
    canAssign: Boolean,
    busy: Boolean,
    error: String?,
    // The way it opens on (the card's step button: Assign, Handle, Solve); the first one otherwise.
    initial: HandleWay?,
    onDismiss: () -> Unit,
    onRemove: (String?) -> Unit,
    onSolve: (String?) -> Unit,
    onAssign: (AdminCardDto?, String?, Boolean) -> Unit
) {
    val removal = isRemoval(request)
    val ways = listOfNotNull(HandleWay.REMOVE.takeIf { removal }, HandleWay.ASSIGN.takeIf { canAssign }, HandleWay.SOLVED)
    var way by remember(request.id) { mutableStateOf(initial?.takeIf { it in ways } ?: ways.first()) }
    var checked by remember(request.id) { mutableStateOf(false) }
    var helper by remember(request.id) { mutableStateOf<String?>(null) }
    var putIn by remember(request.id) { mutableStateOf(true) }
    var note by remember(request.id) { mutableStateOf("") }
    var assignNote by remember(request.id) { mutableStateOf("") }
    val target = request.targetName ?: "this owner"
    val restaurant = request.restaurantName ?: "the restaurant"
    val choices = listOfNotNull(self) + admins.filter { it.id != self?.id }
    val assignee = choices.firstOrNull { it.id == helper }
    val toSelf = assignee != null && assignee.id == self?.id
    // Whether the picked support admin already works in this restaurant (every restaurant, or this one given to them); only the
    // Super Admin gives restaurants.
    val hasIt = assignee != null && (toSelf || assignee.allRestaurants || assignee.restaurants.any { it.id == request.restaurantId })
    val give = assignee != null && !hasIt && putIn && superAdmin
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
                HandleWay.ASSIGN -> KitButton(assignee?.let { if (toSelf) "Take it" else "Assign to ${it.name}" } ?: "Assign",
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
                choices.forEach { admin ->
                    val already = admin.allRestaurants || admin.restaurants.any { it.id == request.restaurantId }
                    AdminChoice(admin, when {
                        admin.id == request.assignedToId -> "Has it now"
                        admin.id == self?.id -> "You"
                        already -> "In $restaurant"
                        else -> null
                    }, helper == admin.id, isSelf = admin.id == self?.id) { helper = admin.id }
                }
                if (toSelf) {
                    Text("It's yours: it goes to Assigned, and you start it when you begin.", fontFamily = Inter(), fontSize = 13.sp, lineHeight = 19.sp,
                        color = Kit.Muted)
                }
                assignee?.takeUnless { toSelf }?.let { who ->
                    StepTitle(2, "Put ${who.name} in $restaurant?")
                    if (hasIt) {
                        Text("${who.name} already takes care of $restaurant.", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted)
                    } else if (!superAdmin) {
                        Text("Only the Super Admin gives ${who.name} $restaurant. They get the request only.", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            com.saporini.mobile_desktop.core.components.ChoiceChip("Yes, put them in", putIn, { putIn = true })
                            com.saporini.mobile_desktop.core.components.ChoiceChip("No, only the request", !putIn, { putIn = false })
                        }
                    }
                    Text(
                        when {
                            request.kind == "NEED_ADMIN" && give -> "${who.name} gets $restaurant and takes care of it. That solves the request: the owner is told."
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
private fun AdminChoice(admin: AdminCardDto, tag: String?, selected: Boolean, isSelf: Boolean = false, onPick: () -> Unit) {
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
                    isSelf -> "Do it yourself"
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
