package com.saporini.mobile_desktop.platform.ui

import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.saporini.mobile_desktop.core.components.RefreshButton
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
    val list = rememberLazyListState()
    LaunchedEffect(state.search, state.filter) { list.scrollToItem(0) }
    val nearEnd by remember(list) {
        derivedStateOf {
            val last = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= list.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, state.hasNext, state.loading, state.loadingMore, state.items.size) {
        if (nearEnd && state.error == null) model.loadMore()
    }
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 960.dp).fillMaxSize(),
            state = list,
            contentPadding = PaddingValues(start = side, end = side, top = 4.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "title") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BackArrow("Back to the restaurants", onBack)
                    Column(Modifier.weight(1f)) {
                        Text("Restaurant applications", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (narrow) 18.sp else 21.sp, color = Kit.Ink)
                        Text(
                            when {
                                !state.loaded -> "People who applied to open a restaurant"
                                state.filter == ApplicationFilter.WAITING -> if (state.total == 1L) "1 application is waiting for review" else "${state.total} applications are waiting for review"
                                else -> if (state.total == 1L) "1 rejected application" else "${state.total} rejected applications"
                            },
                            fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted
                        )
                    }
                    RefreshButton(state.loading, model::refresh)
                }
            }
            if (state.canRead) item(key = "controls") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ApplicationFilter.entries.forEach { filter -> ChoiceChip(filter.label, state.filter == filter, { model.filter(filter) }) }
                    SearchField(query, { query = it.take(100) }, if (narrow) Modifier.weight(1f) else Modifier.width(320.dp),
                        placeholder = "Search by restaurant, email or owner", height = 44.dp)
                }
            }
            when {
                !state.canRead -> item(key = "denied") {
                    PageState(PageStateKind.NO_ACCESS, "Super admins only", "Only the Super Admin and Co-Super-Admins review restaurant applications.")
                }
                !state.loaded && state.loading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Kit.Green) }
                }
                !state.loaded && state.error != null -> item(key = "failed") {
                    PageState(PageStateKind.FAILED, action = { RetryText(onClick = model::refresh) })
                }
                state.items.isEmpty() -> item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                        if (state.search.isNotBlank()) OverviewEmpty("No application matches", "Try another restaurant name, email or owner.", Icons.Outlined.Search)
                        else if (state.filter == ApplicationFilter.WAITING) OverviewEmpty("Nothing to review", "New applications show up here, oldest first.", Icons.Outlined.Inbox)
                        else OverviewEmpty("No rejected applications", "Applications you reject stay here with their reason.", Icons.Outlined.Inbox)
                    }
                }
                else -> items(state.items, key = { it.id }) { application -> ApplicationCard(application, state, model, narrow) }
            }
            if (state.hasNext || state.loadingMore) item(key = "more") {
                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                    if (state.loadingMore) CircularProgressIndicator(Modifier.size(22.dp), color = Kit.Green, strokeWidth = 2.dp)
                    else if (state.error != null) RetryText(onClick = model::loadMore)
                    else KitButton("Show more", model::loadMore)
                }
            }
        }
    }
    state.review?.let { ReviewDialog(it, model) }
}

@Composable
private fun ApplicationCard(application: RestaurantApplicationDto, state: ApplicationsState, model: RestaurantApplicationsScreenModel, narrow: Boolean) {
    val zone = remember { TimeZone.currentSystemDefault() }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).background(Kit.GreenSoft, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Storefront, null, Modifier.size(20.dp), tint = Kit.Green)
                }
                Column(Modifier.weight(1f)) {
                    Text(application.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(application.legalName.takeIf { it.isNotBlank() && it != application.name },
                        application.submittedAt?.let { "applied ${dateTimeText(it, zone)}" }).joinToString(" · "),
                        fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (application.status == "PENDING" && state.canReview && !narrow) ReviewButtons(application, model)
            }
            val facts: List<Pair<ImageVector, String>> = listOfNotNull(
                application.email?.let { Icons.Outlined.Mail to it },
                application.phone?.let { Icons.Outlined.Phone to it },
                application.owner?.let { owner -> Icons.Outlined.Person to listOfNotNull(owner.displayName.ifBlank { null }, owner.email, owner.username?.let { "@$it" }).joinToString(" · ") }
            )
            facts.forEach { (icon, text) -> FooterLine(icon, text) }
            Text("${currencyName(application.currency)} · ${timeZoneLabel(application.timezone)}", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Faint)
            application.rejectionReason?.takeIf { application.status == "REJECTED" }?.let { reason ->
                MessageBar("Rejected: $reason", MessageKind.WARNING)
            }
            if (application.status == "PENDING" && state.canReview && narrow) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { ReviewButtons(application, model) }
        }
    }
}

@Composable
private fun ReviewButtons(application: RestaurantApplicationDto, model: RestaurantApplicationsScreenModel) {
    KitButton("Reject", { model.startReject(application) }, icon = Icons.Outlined.Close, style = ButtonStyle.SECONDARY)
    KitButton("Approve", { model.startApprove(application) }, icon = Icons.Outlined.Check)
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
