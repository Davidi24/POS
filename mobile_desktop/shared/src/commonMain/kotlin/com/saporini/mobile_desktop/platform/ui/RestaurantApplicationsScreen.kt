package com.saporini.mobile_desktop.platform.ui

import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Undo
import com.saporini.mobile_desktop.core.components.ConfirmDialog
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import com.saporini.mobile_desktop.core.components.KitDivider
import com.saporini.mobile_desktop.core.components.OverviewTabs
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.workspace_restaurants
import org.jetbrains.compose.resources.painterResource
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import com.saporini.mobile_desktop.core.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.BindToLifecycle
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.ChoiceChip
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.PageState
import com.saporini.mobile_desktop.core.components.PageStateKind
import com.saporini.mobile_desktop.core.components.RetryText
import com.saporini.mobile_desktop.core.components.ScreenMessages
import com.saporini.mobile_desktop.core.components.SearchField
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.format.dateTimeText
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.platform.ApplicationFilter
import com.saporini.mobile_desktop.platform.ApplicationsState
import com.saporini.mobile_desktop.platform.MAX_REJECTION_REASON
import com.saporini.mobile_desktop.platform.RestaurantApplicationDto
import com.saporini.mobile_desktop.platform.RestaurantApplicationsScreenModel
import com.saporini.mobile_desktop.platform.ReviewStep
import com.saporini.mobile_desktop.platform.currencyName
import com.saporini.mobile_desktop.platform.reviewProblem
import com.saporini.mobile_desktop.platform.timeZoneLabel
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import org.koin.compose.koinInject

/** Super admins: the restaurant applications waiting for review (and the rejected ones), opened from the restaurant list. */
object RestaurantApplicationsScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val model = koinInject<RestaurantApplicationsScreenModel>()
        BindToLifecycle(model::setActive, model::onDispose)
        val state by model.state.collectAsState()
        val session = koinInject<com.saporini.mobile_desktop.core.session.SessionManager>()
        val user by session.currentUser.collectAsState()
        val workspaceModel = koinInject<com.saporini.mobile_desktop.workspace.ui.WorkspaceScreenModel>()
        val workspaceState by workspaceModel.state.collectAsState()
        LaunchedEffect(workspaceState.loggedOut) {
            if (workspaceState.loggedOut) navigator?.replaceAll(com.saporini.mobile_desktop.auth.ui.login.LoginScreen)
        }
        BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFFFCFCFB))) {
            val narrow = maxWidth < 760.dp
            com.saporini.mobile_desktop.workspace.ui.PageDecorations(Modifier.fillMaxSize(), narrow)
            val hideBar = com.saporini.mobile_desktop.core.components.rememberHideOnScroll(narrow)
            Column(Modifier.fillMaxSize()) {
                com.saporini.mobile_desktop.core.components.HideOnScrollBar(hideBar) {
                    com.saporini.mobile_desktop.workspace.ui.PickerTopBar(user, workspaceState.isLoggingOut, workspaceModel::logout, compact = narrow)
                }
                RestaurantApplicationsContent(state, model, narrow, onBack = { navigator?.pop() }, modifier = Modifier.weight(1f).fillMaxWidth().nestedScroll(hideBar.connection))
            }
        }
    }
}

@Composable
internal fun RestaurantApplicationsContent(
    state: ApplicationsState,
    model: RestaurantApplicationsScreenModel,
    narrow: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val side = if (narrow) 16.dp else 36.dp
    ScreenMessages(state.error.takeIf { state.loaded && state.review == null }, state.notice, model::clearMessages)
    var query by remember(state.search) { mutableStateOf(state.search) }
    LaunchedEffect(query) {
        delay(300)
        model.search(query.trim())
    }
    val grid = rememberLazyGridState()
    LaunchedEffect(state.search, state.filter) { grid.scrollToItem(0) }
    // A new page starts at its top.
    LaunchedEffect(state.page) { if (grid.firstVisibleItemIndex > 2) grid.scrollToItem(2) }
    val full: (androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan) = { GridItemSpan(maxLineSpan) }
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            GridCells.Adaptive(if (narrow) 300.dp else 380.dp),
            Modifier.widthIn(max = 1280.dp).fillMaxSize(),
            state = grid,
            contentPadding = PaddingValues(start = side, end = side, top = 4.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item(key = "banner", span = full) { ApplicationsBanner(state, model, narrow, onBack) }
            if (state.canRead) item(key = "controls", span = full) {
                val waiting = state.waitingCount?.let { "Waiting ($it)" } ?: "Waiting"
                val rejected = state.rejectedCount?.let { "Rejected ($it)" } ?: "Rejected"
                val tabs: @Composable (Modifier) -> Unit = { m ->
                    OverviewTabs(listOf(waiting, rejected), if (state.filter == ApplicationFilter.WAITING) waiting else rejected,
                        { model.filter(if (it == waiting) ApplicationFilter.WAITING else ApplicationFilter.REJECTED) }, m, height = 40.dp)
                }
                if (narrow) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // The reload sits with the list's own controls, not in the banner (user, 2026-11-21: "somewhere better").
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            tabs(Modifier.weight(1f))
                            com.saporini.mobile_desktop.core.components.RefreshButton(state.loading, model::refresh, size = 40.dp)
                        }
                        SearchField(query, { query = it.take(100) }, Modifier.fillMaxWidth(), placeholder = "Search by restaurant, email or owner", height = 44.dp)
                        ApplicationsPager(state, model, Modifier.fillMaxWidth())
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        tabs(Modifier.width(320.dp))
                        SearchField(query, { query = it.take(100) }, Modifier.width(320.dp), placeholder = "Search by restaurant, email or owner", height = 40.dp)
                        Spacer(Modifier.weight(1f))
                        ApplicationsPager(state, model)
                        com.saporini.mobile_desktop.core.components.RefreshButton(state.loading, model::refresh, size = 40.dp)
                    }
                }
            }
            when {
                !state.canRead -> item(key = "denied", span = full) {
                    PageState(PageStateKind.NO_ACCESS, "Super admins only", "Only the Super Admin and Co-Super-Admins review restaurant applications.")
                }
                !state.loaded && state.loading -> item(key = "loading", span = full) {
                    Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Kit.Green) }
                }
                !state.loaded && state.error != null -> item(key = "failed", span = full) {
                    PageState(PageStateKind.FAILED, action = { RetryText(onClick = model::refresh) })
                }
                state.items.isEmpty() -> item(key = "empty", span = full) {
                    Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                        if (state.search.isNotBlank()) OverviewEmpty("No application matches", "Try another restaurant name, email or owner.", Icons.Outlined.Search)
                        else if (state.filter == ApplicationFilter.WAITING) OverviewEmpty("Nothing to review", "New applications show up here, oldest first.", Icons.Outlined.Inbox)
                        else OverviewEmpty("No rejected applications", "Applications you reject stay here with their reason.", Icons.Outlined.Inbox)
                    }
                }
                else -> items(state.items, key = { it.id }) { application ->
                    Box(Modifier.animateItem(fadeInSpec = tween(220), placementSpec = tween(360, easing = FastOutSlowInEasing), fadeOutSpec = tween(260))) {
                        ApplicationCard(application, state, model)
                    }
                }
            }
            if (state.loaded && state.total > state.pageSize) item(key = "pages", span = full) {
                Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) { ApplicationsPager(state, model) }
            }
        }
    }
    state.review?.let { ReviewDialog(it, model) }
    state.reopen?.let { step ->
        ConfirmDialog(
            "Bring ${step.application.name} back?",
            "It waits again with the others and can be approved or rejected anew. The reason you gave goes.",
            "Back to waiting",
            onConfirm = model::confirmReopen, onDismiss = model::cancelReopen, busy = step.saving,
            extra = { step.error?.let { MessageBar(it, MessageKind.ERROR) } }
        )
    }
    state.approved?.let { ApprovedCelebration(it, onDone = model::closeApproved) }
}

/** The plain arrow back (no circle, user rule): leaves this page for the restaurant list. */
@Composable
private fun PlainBackArrow(description: String, onClick: () -> Unit) {
    com.saporini.mobile_desktop.core.ui.HoverTooltip(description) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, description, Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onClick).padding(5.dp), tint = Kit.Ink)
    }
}

/** "1–12 of 30" with ‹ Page 1 of 3 ›, as on Users. */
@Composable
private fun ApplicationsPager(state: ApplicationsState, model: RestaurantApplicationsScreenModel, modifier: Modifier = Modifier) {
    if (!state.loaded || state.total == 0L) return
    val pages = ((state.total + state.pageSize - 1) / state.pageSize).toInt().coerceAtLeast(1)
    val first = state.page * state.pageSize + 1
    val last = minOf((state.page + 1L) * state.pageSize, state.total)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        Text("$first–$last of ${state.total}", Modifier.padding(end = 6.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
            color = Kit.Muted, translate = false)
        PagerArrow(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous page", state.page > 0 && !state.loading) { model.goToPage(state.page - 1) }
        Text("Page ${state.page + 1} of $pages", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink)
        PagerArrow(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next page", state.page < pages - 1 && !state.loading) { model.goToPage(state.page + 1) }
    }
}

@Composable
private fun PagerArrow(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, modifier = Modifier.size(34.dp), shape = RoundedCornerShape(8.dp), color = Color.White,
        border = BorderStroke(1.dp, Kit.Border)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, Modifier.size(18.dp), tint = if (enabled) Kit.Ink else Kit.Faint) }
    }
}

/** "3 d", "6 h", "40 min": how long an application has waited. */
private fun waitedText(submittedAt: String?, now: kotlin.time.Instant): String? {
    val at = com.saporini.mobile_desktop.core.format.instantOrNull(submittedAt) ?: return null
    val minutes = (now - at).inWholeMinutes.coerceAtLeast(0)
    return when {
        minutes < 60 -> "$minutes min"
        minutes < 48 * 60 -> "${minutes / 60} h"
        else -> "${minutes / (24 * 60)} days"
    }
}

/** The page's top, in the restaurant list's look: what it is, how many wait, the oldest wait, how many were rejected. */
@Composable
private fun ApplicationsBanner(state: ApplicationsState, model: RestaurantApplicationsScreenModel, narrow: Boolean, onBack: () -> Unit) {
    val now = remember { kotlin.time.Clock.System.now() }
    val oldest = if (state.filter == ApplicationFilter.WAITING && state.search.isBlank()) waitedText(state.items.firstOrNull()?.submittedAt, now) else null
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
                        PlainBackArrow("Back to the restaurants", onBack)
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(Kit.Green).padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Outlined.Storefront, null, Modifier.size(13.dp), tint = Color.White)
                            Text("NEW RESTAURANTS", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp, color = Color.White)
                        }
                    }
                    Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Restaurant applications", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (narrow) 19.sp else 23.sp, color = Kit.Ink)
                        Text("People who want to open a restaurant with you. Approve to open it and invite its owner, or reject it with a reason.",
                            fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 13.5.sp, lineHeight = 19.sp, color = Kit.Muted)
                        Row(Modifier.padding(top = 6.dp).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                            BannerFigure(state.waitingCount?.toString() ?: "…", "waiting", if ((state.waitingCount ?: 0) > 0) Kit.Amber else Kit.Ink)
                            if (oldest != null) {
                                BannerLine()
                                BannerFigure(oldest, "oldest wait", Kit.Ink)
                            }
                            BannerLine()
                            BannerFigure(state.rejectedCount?.toString() ?: "…", "rejected", Kit.Ink)
                        }
                    }
                }
                if (!narrow) {
                    Image(painterResource(Res.drawable.workspace_restaurants), null, Modifier.padding(start = 24.dp).size(180.dp), contentScale = ContentScale.Fit)
                }
            }
        }
    }
}

@Composable
private fun BannerFigure(value: String, label: String, color: Color) {
    Column(Modifier.padding(end = 22.dp)) {
        Text(value, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 22.sp, color = color, translate = false)
        Text(label, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Kit.Muted)
    }
}

@Composable
private fun BannerLine() {
    Box(Modifier.padding(end = 22.dp).width(1.dp).fillMaxHeight().background(Kit.Border))
}

/**
 * One application as the restaurant it would become (user, 2026-11-21: "I don't like this page, make it better"): its colour
 * band with how long it waited, the logo square on the edge, name and what it is, a strip of currency / time zone / wait, how
 * guests reach it, the future owner in their own box, and Reject / Approve across the foot.
 */
@Composable
private fun ApplicationCard(application: RestaurantApplicationDto, state: ApplicationsState, model: RestaurantApplicationsScreenModel) {
    val zone = remember { TimeZone.currentSystemDefault() }
    val now = remember { kotlin.time.Clock.System.now() }
    val accent = accentOf(application.id)
    // Approved a moment ago: green until the celebration closes and the card leaves.
    val approved = state.approved?.application?.id == application.id
    val rejected = application.status == "REJECTED"
    val waiting = application.status == "PENDING" && !approved
    val waited = waitedText(application.submittedAt, now)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val lift by animateDpAsState(if (hovered) 12.dp else 2.dp, label = "application-lift")
    val shape = RoundedCornerShape(18.dp)
    Surface(
        Modifier.fillMaxWidth().hoverable(interaction).shadow(lift, shape, ambientColor = Color(0x16000000), spotColor = Color(0x1C000000)),
        shape = shape, color = Color.White,
        border = BorderStroke(if (approved) 2.dp else 1.dp, when {
            approved -> Kit.Green
            hovered -> accent.strong.copy(alpha = 0.45f)
            else -> Kit.Border
        })
    ) {
        Box {
            Column {
                Box(Modifier.fillMaxWidth().height(100.dp)) {
                    CoverBackground(null, accent)
                    if (approved) Box(Modifier.fillMaxSize().background(Kit.Green.copy(alpha = 0.18f)))
                    val (badge, badgeColor) = when {
                        approved -> "Approved" to Kit.Green
                        rejected -> "Rejected" to Kit.Danger
                        waited != null -> "Waiting · $waited" to Kit.Amber
                        else -> "Waiting" to Kit.Amber
                    }
                    Row(
                        Modifier.align(Alignment.TopStart).padding(12.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.95f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (approved) Icon(Icons.Outlined.Check, null, Modifier.size(13.dp), tint = badgeColor)
                        else Box(Modifier.size(7.dp).clip(CircleShape).background(badgeColor))
                        Text(badge, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = badgeColor, maxLines = 1)
                    }
                    Text(
                        application.submittedAt?.let { "Applied ${com.saporini.mobile_desktop.core.format.agoText(it, now, zone)}" } ?: "",
                        Modifier.align(Alignment.TopEnd).padding(14.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.8f))
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Ink, maxLines = 1
                    )
                }
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 38.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(application.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Kit.Ink, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, translate = false)
                        Text(application.legalName.takeIf { it.isNotBlank() && it != application.name } ?: "Same legal name", fontFamily = Inter(),
                            fontSize = 12.5.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, translate = false)
                    }
                    Text(application.description?.takeIf { it.isNotBlank() } ?: "No description given.", fontFamily = Inter(), fontSize = 13.sp,
                        lineHeight = 18.sp, color = if (application.description.isNullOrBlank()) Kit.Faint else Kit.Ink, minLines = 2, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, translate = application.description.isNullOrBlank())
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(12.dp)).background(Kit.Canvas).padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StripCell(application.currency.ifBlank { "–" }, currencyName(application.currency))
                        StripLine()
                        StripCell(timeZoneLabel(application.timezone).substringBefore(" ("), "time zone")
                        StripLine()
                        StripCell(waited ?: "–", if (rejected) "waited" else "waiting")
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FooterLine(Icons.Outlined.Mail, application.email ?: "No email")
                        FooterLine(Icons.Outlined.Phone, application.phone ?: "No phone")
                        FooterLine(Icons.Outlined.Language, application.website ?: "No website", if (application.website == null) Kit.Faint else Kit.Muted)
                    }
                    application.owner?.let { owner ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(accent.softer).border(1.dp, accent.soft, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            com.saporini.mobile_desktop.core.components.InitialsAvatar(owner.displayName.ifBlank { owner.email ?: "?" }, color = accent.strong, size = 38.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(owner.displayName.ifBlank { "No name" }, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp,
                                        color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, translate = false)
                                    Text("OWNER", Modifier.clip(RoundedCornerShape(50)).background(accent.strong).padding(horizontal = 6.dp, vertical = 1.dp),
                                        fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 0.5.sp, color = Color.White)
                                }
                                Text(listOfNotNull(owner.username?.let { "@$it" }, owner.phone).joinToString(" · "), fontFamily = Inter(), fontSize = 11.5.sp,
                                    color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, translate = false)
                                owner.email?.let { Text(it, fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, translate = false) }
                            }
                        }
                    }
                    application.rejectionReason?.takeIf { rejected }?.let { reason ->
                        MessageBar("Rejected: $reason", MessageKind.WARNING)
                    }
                    if (rejected && state.canReview) {
                        KitDivider()
                        KitButton("Back to waiting", { model.startReopen(application) }, Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Outlined.Undo,
                            style = ButtonStyle.SECONDARY)
                    }
                    if (waiting && state.canReview) {
                        KitDivider()
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KitButton("Reject", { model.startReject(application) }, Modifier.weight(1f), icon = Icons.Outlined.Close, style = ButtonStyle.DANGER_OUTLINE)
                            KitButton("Approve", { model.startApprove(application) }, Modifier.weight(1f), icon = Icons.Outlined.Check)
                        }
                    }
                }
            }
            // The logo square on the band's edge, as on the restaurant cards.
            Box(
                Modifier.padding(start = 20.dp, top = 64.dp).size(64.dp).shadow(6.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp)).background(Color.White).padding(3.dp)
            ) {
                LogoTile(null, application.name, accent, Modifier.fillMaxSize().clip(RoundedCornerShape(15.dp)), 21)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StripCell(value: String, label: String) {
    Column(Modifier.weight(1f).padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(value, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StripLine() {
    Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 4.dp).background(Kit.Border))
}

@Composable
private fun ReviewDialog(step: ReviewStep, model: RestaurantApplicationsScreenModel) {
    val application = step.application
    val owner = application.owner?.displayName?.ifBlank { null } ?: "The applicant"
    AppDialog(
        if (step.approve) "Approve ${application.name}?" else "Reject ${application.name}?", model::cancelReview,
        busy = step.saving, maxWidth = 560.dp,
        buttons = {
            KitButton("Cancel", model::cancelReview, style = ButtonStyle.SECONDARY, enabled = !step.saving)
            if (step.approve) KitButton("Approve", model::confirmReview, icon = Icons.Outlined.Check, loading = step.saving)
            else KitButton("Reject", model::confirmReview, icon = Icons.Outlined.Close, style = ButtonStyle.DANGER, loading = step.saving)
        }
    ) {
        step.error?.let { MessageBar(it, MessageKind.ERROR) }
        if (step.approve) {
            Text("${application.name} opens with a first branch and goes last on the restaurant list. $owner gets an email to set a password " +
                "and becomes its owner.", fontFamily = Inter(), fontSize = 14.sp, lineHeight = 20.sp, color = Kit.Ink)
        } else {
            Text("$owner gets an email with your reason and can see it with their application code. They can apply again later.",
                fontFamily = Inter(), fontSize = 14.sp, lineHeight = 20.sp, color = Kit.Ink)
            TextInput(step.reason, model::editReason, Modifier.fillMaxWidth(), label = "Reason", required = true, singleLine = false, minLines = 3,
                maxLength = MAX_REJECTION_REASON, placeholder = "What's missing or why it can't be approved",
                problem = if (step.showProblems) reviewProblem(step) else null)
        }
    }
}
