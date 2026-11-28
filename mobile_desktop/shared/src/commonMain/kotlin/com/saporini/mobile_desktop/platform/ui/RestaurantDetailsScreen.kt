package com.saporini.mobile_desktop.platform.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.outlined.Palette
import com.saporini.mobile_desktop.hours.HoursBranch
import com.saporini.mobile_desktop.hours.OpeningHoursModel
import com.saporini.mobile_desktop.platform.BranchView
import kotlinx.datetime.toLocalDateTime
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.inventory_stock_value
import mobile_desktop.shared.generated.resources.order_empty_dining
import mobile_desktop.shared.generated.resources.overview_guests
import mobile_desktop.shared.generated.resources.overview_next_hours
import mobile_desktop.shared.generated.resources.overview_reservations
import mobile_desktop.shared.generated.resources.role_finance
import mobile_desktop.shared.generated.resources.settings_payments
import mobile_desktop.shared.generated.resources.settings_reservations
import mobile_desktop.shared.generated.resources.supplier_place
import mobile_desktop.shared.generated.resources.users_owner
import mobile_desktop.shared.generated.resources.workspace_restaurants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddBusiness
import androidx.compose.material.icons.outlined.AddLocationAlt
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.LocationOn
import mobile_desktop.shared.generated.resources.inventory_places
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.WarningAmber
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.positionInParent
import com.saporini.mobile_desktop.core.components.fadingEdges
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.BindToLifecycle
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.CellText
import com.saporini.mobile_desktop.core.components.CheckRow
import com.saporini.mobile_desktop.core.components.CheckState
import com.saporini.mobile_desktop.core.components.ConfirmDialog
import com.saporini.mobile_desktop.core.components.FieldPair
import com.saporini.mobile_desktop.core.components.GroupLabel
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.KitDivider
import com.saporini.mobile_desktop.core.components.LabeledValue
import com.saporini.mobile_desktop.core.components.ListPanel
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.OverviewPageSkeleton
import com.saporini.mobile_desktop.core.components.OverviewPanel
import com.saporini.mobile_desktop.core.components.OverviewStatCard
import com.saporini.mobile_desktop.core.components.OverviewTabs
import com.saporini.mobile_desktop.core.components.PageHeader
import com.saporini.mobile_desktop.core.components.PageState
import com.saporini.mobile_desktop.core.components.PageStateKind
import com.saporini.mobile_desktop.core.components.RefreshButton
import com.saporini.mobile_desktop.core.components.RetryText
import com.saporini.mobile_desktop.core.components.RowAction
import com.saporini.mobile_desktop.core.components.RowActionsMenu
import com.saporini.mobile_desktop.core.components.ScreenMessages
import com.saporini.mobile_desktop.core.components.SearchableSelect
import com.saporini.mobile_desktop.core.components.SelectInput
import com.saporini.mobile_desktop.core.components.StatusChip
import com.saporini.mobile_desktop.core.components.SummaryCardRow
import com.saporini.mobile_desktop.core.components.TableRow
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.components.ToggleRow
import com.saporini.mobile_desktop.core.components.ToolbarButton
import com.saporini.mobile_desktop.core.components.ToolbarPrimaryButton
import com.saporini.mobile_desktop.core.format.dateTimeText
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.RemoteImage
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.core.ui.screenSizeFor
import com.saporini.mobile_desktop.platform.effectiveAt
import com.saporini.mobile_desktop.platform.ADDRESS_TYPES
import com.saporini.mobile_desktop.platform.AddressDraft
import com.saporini.mobile_desktop.platform.BranchDraft
import com.saporini.mobile_desktop.platform.CONTACT_TYPES
import com.saporini.mobile_desktop.platform.CURRENCIES
import com.saporini.mobile_desktop.platform.ContactDraft
import com.saporini.mobile_desktop.platform.CurrencyFlow
import com.saporini.mobile_desktop.platform.DetailsDraft
import com.saporini.mobile_desktop.platform.MAX_BRANCH_NAME
import com.saporini.mobile_desktop.platform.MAX_CITY
import com.saporini.mobile_desktop.platform.MAX_CODE
import com.saporini.mobile_desktop.platform.MAX_DESCRIPTION
import com.saporini.mobile_desktop.platform.MAX_EMAIL_TEXT
import com.saporini.mobile_desktop.platform.MAX_LEGAL_NAME
import com.saporini.mobile_desktop.platform.MAX_POSTAL
import com.saporini.mobile_desktop.platform.MAX_RECEIPT_TEXT
import com.saporini.mobile_desktop.platform.MAX_RESTAURANT_NAME
import com.saporini.mobile_desktop.platform.MAX_STREET
import com.saporini.mobile_desktop.platform.MAX_TAX_FIELD
import com.saporini.mobile_desktop.platform.MAX_WEBSITE
import com.saporini.mobile_desktop.platform.ROUNDING_STEPS
import com.saporini.mobile_desktop.platform.RestaurantConfirm
import com.saporini.mobile_desktop.platform.RestaurantDetailsScreenModel
import com.saporini.mobile_desktop.platform.RestaurantDetailsState
import com.saporini.mobile_desktop.platform.RestaurantTab
import com.saporini.mobile_desktop.platform.TIME_ZONES
import com.saporini.mobile_desktop.platform.TaxDraft
import com.saporini.mobile_desktop.platform.TimezoneFlow
import com.saporini.mobile_desktop.platform.addressProblems
import com.saporini.mobile_desktop.platform.addressTypeLabel
import com.saporini.mobile_desktop.platform.branchProblems
import com.saporini.mobile_desktop.platform.branchStatusLabel
import com.saporini.mobile_desktop.platform.contactProblems
import com.saporini.mobile_desktop.platform.contactTypeLabel
import com.saporini.mobile_desktop.platform.currencyName
import com.saporini.mobile_desktop.platform.detailsProblems
import com.saporini.mobile_desktop.platform.restaurantStatusLabel
import com.saporini.mobile_desktop.platform.taxProblems
import com.saporini.mobile_desktop.platform.timeZoneLabel
import com.saporini.mobile_desktop.pos.menu.ui.item.MaxPhotoBytes
import com.saporini.mobile_desktop.pos.menu.ui.item.photoContentType
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.koin.compose.koinInject

/** The Restaurant app (super admins): one tab of the entered restaurant's details. */
@Composable
internal fun RestaurantDetailsScreen(tab: RestaurantTab, onTab: (RestaurantTab) -> Unit, modifier: Modifier = Modifier, onUnsavedChange: (Boolean) -> Unit = {}) {
    val model = koinInject<RestaurantDetailsScreenModel>()
    BindToLifecycle(model::setActive, model::onDispose)
    val state by model.state.collectAsState()
    LaunchedEffect(state.brandingChanged) { onUnsavedChange(state.brandingChanged) }
    val navigator = LocalNavigator.current
    val session = koinInject<SessionManager>()
    // A removed restaurant: back to the restaurant list.
    LaunchedEffect(state.removed) {
        if (state.removed) {
            session.leaveRestaurant()
            navigator?.replaceAll(RestaurantListScreen)
        }
    }
    RestaurantDetailsContent(state, model, tab, onTab, showTabs = false, modifier = modifier)
}

/** Admin Hub → Restaurant info: what the owner keeps up to date (the rest is shown, changed by the Super Admin). */
@Composable
internal fun RestaurantInfoScreen(modifier: Modifier = Modifier, global: Boolean = false) {
    val model = koinInject<RestaurantDetailsScreenModel>()
    BindToLifecycle(model::setActive, model::onDispose)
    val state by model.state.collectAsState()
    RestaurantDetailsContent(state, model, state.tab, model::tab, showTabs = true, modifier = modifier, global = global)
}

/** Admin Hub → Branches (looking at every branch, step 26): the branch cards, each opening its own page. */
@Composable
internal fun RestaurantBranchesScreen(modifier: Modifier = Modifier) {
    val model = koinInject<RestaurantDetailsScreenModel>()
    BindToLifecycle(model::setActive, model::onDispose)
    val state by model.state.collectAsState()
    RestaurantDetailsContent(state, model, RestaurantTab.BRANCHES, {}, showTabs = false, modifier = modifier)
}

/**
 * Restaurant info (redone 2026-11-22, user: "make them look much better; every branch can have them different, so you go
 * inside a branch"): the restaurant on its picture, then its parts. A branch's own page (Branches → a branch) has its
 * details, address and opening hours. Legal & tax and Currency & time zone sit apart at the end: dangerous to change.
 */
@Composable
internal fun RestaurantDetailsContent(
    state: RestaurantDetailsState,
    model: RestaurantDetailsScreenModel,
    tab: RestaurantTab,
    onTab: (RestaurantTab) -> Unit,
    showTabs: Boolean,
    modifier: Modifier = Modifier,
    // Admin Hub looking at every branch (step 26): the restaurant only, no branch's own parts; branches have their own place.
    global: Boolean = false
) {
    // Admin Hub is entered inside a branch (user, 2026-11-23): that branch's hours and contacts show here and there's no
    // list of branches. The Super Admin's Restaurant app is outside any branch: it has Branches, each with its own page.
    val inBranch = showTabs && !global && state.branchId != null
    val here = state.branchId?.takeIf { inBranch }?.let { id -> state.branches.firstOrNull { it.id == id } }
    val tabs = when {
        // Someone with only "Change opening hours" (a manager) sees just those of their branch.
        !state.canEdit -> listOf(if (inBranch) RestaurantTab.HOURS else RestaurantTab.BRANCHES)
        else -> RestaurantTab.entries.filter { t ->
            when (t) {
                RestaurantTab.BRANCHES -> !inBranch && !global
                RestaurantTab.HOURS -> inBranch
                RestaurantTab.MONEY -> state.superAdmin
                else -> true
            }
        }
    }
    val shown = if (tab in tabs) tab else tabs.first()
    // Opening hours have a model of their own, alive while this page is.
    val hoursModel = remember { org.koin.mp.KoinPlatform.getKoinOrNull()?.getOrNull<OpeningHoursModel>() }
    androidx.compose.runtime.DisposableEffect(hoursModel) { onDispose { hoursModel?.close() } }
    val hoursState = hoursModel?.state?.collectAsState()?.value
    val branch = state.openBranch?.takeIf { shown == RestaurantTab.BRANCHES }
    // A branch's page brings its own opening hours; inside a branch, its hours are the Opening hours tab.
    val hoursOf = branch ?: here?.takeIf { shown == RestaurantTab.HOURS }
    LaunchedEffect(hoursOf?.id, state.restaurantId) {
        if (hoursOf != null) hoursModel?.setBranches(state.restaurantId, listOf(HoursBranch(hoursOf.id, hoursOf.name)))
    }
    // Another part picked in the bar leaves the branch's page.
    LaunchedEffect(shown) { if (shown != RestaurantTab.BRANCHES && state.openBranchId != null) model.leaveBranch() }
    // The Details tab is the form itself: it holds the saved details, ready to change in place (again after a save or Discard).
    LaunchedEffect(shown, state.details == null, state.restaurant?.version, state.canEdit) {
        if (shown == RestaurantTab.DETAILS && state.details == null && state.canEdit && state.restaurant != null) model.openDetailsForm()
    }
    // So is a branch's page, and inside a branch Details' "This branch".
    val formOf = branch ?: here?.takeIf { shown == RestaurantTab.DETAILS }
    LaunchedEffect(formOf?.id, formOf?.version, state.branch == null, state.canEdit, formOf?.let { state.branchAddresses[it.id] }) {
        if (formOf != null && state.branch == null && state.canEdit) model.openBranchForm(formOf.id)
    }
    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        val size = screenSizeFor(maxWidth)
        val wide = maxWidth >= 900.dp
        val side = if (size.isPhone) 14.dp else 22.dp
        val contentWidth = maxWidth - side * 2
        val hoursChanged = shown == RestaurantTab.HOURS && hoursState?.changed == true
        val unsaved = when {
            branch != null -> state.branchView == BranchView.DETAILS && branchChanged(state)
            // Details' cards save themselves (Cancel / Save at their foot).
            shown == RestaurantTab.DETAILS -> false
            shown == RestaurantTab.BRANDING -> state.canEdit && (state.brandingChanged || com.saporini.mobile_desktop.platform.ReceiptLooks.changed(state.restaurantId))
            else -> false
        }
        Column(Modifier.fillMaxSize().padding(horizontal = side, vertical = if (size.isPhone) 12.dp else 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ScreenMessages(state.error.takeIf { !anyDialogOpen(state) && !unsaved }, state.notice, model::clearMessages)
            when {
                !state.canRead -> PageState(PageStateKind.EMPTY, "Access unavailable", "You do not have permission to view restaurant details.")
                state.restaurantId == null -> PageState(PageStateKind.EMPTY, "No restaurant", "Open a restaurant first.")
                !state.loaded && state.loading -> OverviewPageSkeleton(size)
                !state.loaded && state.error != null -> PageState(PageStateKind.FAILED, action = { RetryText(onClick = model::refresh) })
                // Before its first answer the page shows its shapes too, never a spinner.
                state.restaurant == null -> OverviewPageSkeleton(size)
                // A branch's opening hours scroll on their own, under the branch's band.
                branch != null && state.branchView == BranchView.HOURS && hoursModel != null && hoursState != null -> {
                    BranchBand(branch, hoursState.hours?.now, state, model, size.isPhone)
                    BranchSwitchRow(branch, state, model, size.isPhone)
                    com.saporini.mobile_desktop.hours.ui.OpeningHoursTab(hoursState, hoursModel, size, Modifier.fillMaxWidth().weight(1f))
                }
                else -> Box(Modifier.fillMaxWidth().weight(1f)) {
                    com.saporini.mobile_desktop.core.components.ScrollColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (branch != null) {
                            BranchBand(branch, hoursState?.hours?.now, state, model, size.isPhone)
                            BranchSwitchRow(branch, state, model, size.isPhone)
                            BranchDetails(branch, state, model, size, wide, hoursState)
                        } else {
                            RestaurantHero(state, model, size, here)
                            if (showTabs && tabs.size > 1) RestaurantTabs(tabs, shown, { t -> com.saporini.mobile_desktop.core.components.LeaveGuard.attempt { onTab(t) } }, wide)
                            when (shown) {
                                RestaurantTab.DETAILS -> DetailsTab(state, model, size, wide, here)
                                RestaurantTab.BRANCHES -> BranchesTab(state, model, size, contentWidth)
                                RestaurantTab.HOURS -> if (hoursModel != null && hoursState != null) {
                                    here?.let { HereLine(it, state) }
                                    com.saporini.mobile_desktop.hours.ui.OpeningHoursItems(hoursState, hoursModel, size)
                                }
                                RestaurantTab.BRANDING -> BrandingTab(state, model, size, wide)
                                RestaurantTab.MONEY -> MoneyTab(state, model, size, wide)
                            }
                        }
                        Spacer(Modifier.height(if (unsaved || hoursChanged) 96.dp else 28.dp))
                    }
                    if (hoursChanged && hoursModel != null && hoursState != null) {
                        com.saporini.mobile_desktop.hours.ui.OpeningHoursBar(hoursState, hoursModel, size,
                            Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
                    }
                    if (unsaved) {
                        val bar = when {
                            branch != null -> Triple(state.branch?.takeIf { it.showProblems }?.let { d -> branchProblems(d).values.firstOrNull() },
                                (model::cancelBranch to model::saveBranch), "You changed the branch")
                            shown == RestaurantTab.BRANDING -> Triple(null, ({ discardReceipt(state, model) } to { saveReceipt(state, model) }), "You changed the receipt")
                            else -> Triple(state.details?.takeIf { it.showProblems }?.let { d -> detailsProblems(d).values.firstOrNull() }
                                ?: state.branch?.takeIf { it.showProblems && here != null }?.let { d -> branchProblems(d).values.firstOrNull() },
                                (model::discardDetailsAndBranch to model::saveDetailsAndBranch), "You changed the details")
                        }
                        com.saporini.mobile_desktop.core.components.UnsavedChangesBar(
                            saving = state.saving, problem = bar.first, error = state.error,
                            onDiscard = bar.second.first, onSave = bar.second.second,
                            message = if (size.isPhone) "Unsaved changes" else bar.third,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)
                        )
                    }
                }
            }
        }
        // Add branch: the green tab on the right edge, as on the Super Admin's pages (user, 2026-11-21).
        if (branch == null && shown == RestaurantTab.BRANCHES && state.canManage && state.loaded) {
            var position by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0.3f) }
            val maxOffset = with(androidx.compose.ui.platform.LocalDensity.current) {
                (maxHeight - com.saporini.mobile_desktop.core.components.edgeActionButtonHeight(size.isPhone)).coerceAtLeast(0.dp).toPx()
            }
            com.saporini.mobile_desktop.core.components.RightEdgeActionButton(
                "Add branch", model::newBranch, enabled = !state.saving, width = if (size.isPhone) 108.dp else 132.dp, compact = size.isPhone,
                modifier = Modifier.align(Alignment.TopEnd)
                    .offset { androidx.compose.ui.unit.IntOffset(0, kotlin.math.round(position * maxOffset).toInt()) }
                    .draggable(
                        orientation = androidx.compose.foundation.gestures.Orientation.Vertical,
                        state = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
                            if (maxOffset > 0f) position = (position + delta / maxOffset).coerceIn(0f, 1f)
                        }
                    )
            )
        }
    }
    // The receipt asks before the person leaves it with changes (user, 2026-11-23). Save waits for the texts to be saved.
    var leaveAfterSave by remember { mutableStateOf<(() -> Unit)?>(null) }
    val receiptUnsaved = shown == RestaurantTab.BRANDING && state.canEdit &&
        (state.brandingChanged || com.saporini.mobile_desktop.platform.ReceiptLooks.changed(state.restaurantId))
    com.saporini.mobile_desktop.core.components.GuardUnsaved(receiptUnsaved, "You changed the receipt and didn't save it.",
        save = { leave -> saveReceipt(state, model); leaveAfterSave = leave },
        discard = { discardReceipt(state, model) })
    LaunchedEffect(state.saving, state.brandingChanged, com.saporini.mobile_desktop.platform.ReceiptLooks.changed(state.restaurantId), leaveAfterSave) {
        val leave = leaveAfterSave ?: return@LaunchedEffect
        if (!state.saving && !state.brandingChanged && !com.saporini.mobile_desktop.platform.ReceiptLooks.changed(state.restaurantId)) { leaveAfterSave = null; leave() }
        else if (!state.saving && state.error != null) leaveAfterSave = null
    }
    com.saporini.mobile_desktop.core.components.LeaveGuardDialog(saving = state.saving && leaveAfterSave != null)
    Dialogs(state, model)
    if (shown == RestaurantTab.HOURS && hoursModel != null && hoursState != null) com.saporini.mobile_desktop.hours.ui.OpeningHoursExtras(hoursState, hoursModel)
}

/** "Main Branch · the branch you work in": whose hours and contacts these are. */
@Composable
private fun HereLine(branch: com.saporini.mobile_desktop.platform.BranchDto, state: RestaurantDetailsState) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(30.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Kit.GreenSoft), Alignment.Center) {
            Icon(Icons.Outlined.Place, null, Modifier.size(17.dp), tint = Kit.Green)
        }
        Text(branch.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink, translate = false)
        Text(if (state.branches.size > 1) "the branch you work in · each branch has its own" else "the branch you work in", fontFamily = Inter(),
            fontSize = 12.5.sp, color = Kit.Muted)
    }
}

private fun anyDialogOpen(state: RestaurantDetailsState) = (state.branch != null && state.branch.id == null) || state.address != null ||
    state.contact != null || state.tax != null || state.currency != null || state.timezone != null || state.confirm != null ||
    state.ownerStep != null || state.transfer != null

// ---- The top of the page ----

/**
 * The restaurant at the top, like a profile: its cover picture (the Super Admin changes it there), the logo half on its
 * edge, the name and whether it's open, and what matters at a glance (code, branches, currency, time zone) as chips.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RestaurantHero(state: RestaurantDetailsState, model: RestaurantDetailsScreenModel, size: ScreenSize, here: com.saporini.mobile_desktop.platform.BranchDto? = null) {
    val restaurant = state.restaurant ?: return
    val name = restaurant.name
    // No brand colours any more (user, 2026-11-23): the band without a cover takes the restaurant's own accent.
    val accent = accentOf(name)
    val phone = size.isPhone
    val logo = if (phone) 76.dp else 108.dp
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border), shadowElevation = 1.dp) {
        Column {
            Box(Modifier.fillMaxWidth().height(if (phone) 120.dp else 190.dp)) {
                CoverBackground(restaurant.coverUrl, accent)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.2f))))
                // The cover can be changed by whoever changes the details (user, 2026-11-23: "the banner should be editable").
                if (state.canEdit) {
                    CoverButton(restaurant.coverUrl != null, "restaurant" in state.coverBusy, { model.uploadCover(null, it) }, { model.removeCover(null) },
                        model::coverProblem, Modifier.align(Alignment.TopEnd).padding(12.dp),
                        label = if (restaurant.coverUrl != null) "Change cover" else "Add a cover")
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = if (phone) 14.dp else 22.dp, end = if (phone) 12.dp else 20.dp, bottom = if (phone) 14.dp else 18.dp),
                horizontalArrangement = Arrangement.spacedBy(if (phone) 12.dp else 18.dp)) {
                HeroLogo(state, model, logo)
                Column(Modifier.weight(1f).padding(top = if (phone) 8.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(if (phone) 6.dp else 9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(name, Modifier.weight(1f, fill = false), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (phone) 20.sp else 27.sp,
                            lineHeight = if (phone) 25.sp else 32.sp, letterSpacing = (-0.3).sp, color = Kit.Ink, maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, translate = false)
                        DotPill(restaurantStatusLabel(restaurant.status, restaurant.isActive), if (restaurant.open) Kit.Green else Kit.Amber)
                    }
                    val openBranches = state.branches.count { b -> b.open }
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FactChip(Icons.Outlined.Badge, restaurant.code)
                        FactChip(if (here != null) Icons.Outlined.Place else Icons.Outlined.Business, when {
                            here != null -> if (state.branches.size > 1) "${here.name} · ${state.branches.size} branches" else here.name
                            state.branches.isEmpty() -> "No branches yet"
                            openBranches == state.branches.size -> if (state.branches.size == 1) "1 branch" else "${state.branches.size} branches"
                            else -> "${state.branches.size} branches · $openBranches open"
                        })
                        FactChip(Icons.Outlined.CurrencyExchange, "${restaurant.currency} · ${currencyName(restaurant.currency)}")
                        FactChip(Icons.Outlined.Schedule, timeZoneLabel(restaurant.timezone))
                    }
                }
                Box(Modifier.padding(top = if (phone) 8.dp else 14.dp)) {
                    RefreshButton(state.loading, model::refresh, size = if (phone) 36.dp else 40.dp)
                }
            }
        }
    }
}

private const val LogoCorner = 0.18f

/**
 * The logo on the card at the top, changed right there (user, 2026-11-23: "make the edit logo in the card above"): a round
 * button on its corner picks a picture (or removes the logo), which is placed in its square first, then saved.
 */
@Composable
private fun HeroLogo(state: RestaurantDetailsState, model: RestaurantDetailsScreenModel, logo: androidx.compose.ui.unit.Dp) {
    val restaurant = state.restaurant ?: return
    var placing by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var menu by remember { mutableStateOf(false) }
    val pick = rememberPicturePicker({ picked ->
        runCatching { picked.bytes.decodeToImageBitmap() }.getOrNull()?.let { placing = it } ?: model.logoProblem("This file can't be opened as a picture.")
    }, model::logoProblem)
    val hasLogo = state.branding?.logoUrl != null
    Box(Modifier.offsetUpBy(logo / 2)) {
        LogoBox(state.branding?.logoUrl, logo, Modifier.shadow(8.dp, RoundedCornerShape(logo * LogoCorner)), busy = state.uploadingLogo) {
            RestaurantLogo(null, restaurant.name, logo)
        }
        if (state.canEdit) Box(Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 6.dp)) {
            com.saporini.mobile_desktop.core.ui.HoverTooltip(if (hasLogo) "Change the logo" else "Add a logo") {
                Box(Modifier.size(34.dp).shadow(3.dp, androidx.compose.foundation.shape.CircleShape).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color.White).border(1.dp, Kit.Border, androidx.compose.foundation.shape.CircleShape)
                    .clickable(enabled = !state.uploadingLogo) { if (hasLogo) menu = true else pick() }, Alignment.Center) {
                    Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(17.dp), tint = Kit.Ink)
                }
            }
            androidx.compose.material3.DropdownMenu(menu, { menu = false }, Modifier.background(Color.White)) {
                androidx.compose.material3.DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Outlined.Upload, null, Modifier.size(18.dp), tint = Kit.Ink) },
                    text = { Text("Choose another picture", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp, color = Kit.Ink) },
                    onClick = { menu = false; pick() })
                androidx.compose.material3.DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp), tint = Kit.Danger) },
                    text = { Text("Remove the logo", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp, color = Kit.Danger) },
                    onClick = { menu = false; model.removeLogo() })
            }
        }
    }
    placing?.let { image ->
        LogoCropDialog(image, busy = state.uploadingLogo, onCancel = { placing = null }) { png ->
            placing = null
            model.uploadLogo(png, "logo.png", "image/png")
        }
    }
}

/**
 * The logo in its tile, the same at the top of the page and in Logo & receipts (user, 2026-11-23: "the logo up should be
 * the same as down"): the whole picture on a soft background with a thin line round it; [empty] while there's none.
 */
@Composable
private fun LogoBox(url: String?, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, busy: Boolean = false,
                    empty: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(size * LogoCorner)
    Box(modifier.size(size).clip(shape).background(Kit.Canvas).border(1.dp, Kit.Border, shape), Alignment.Center) {
        if (busy) CircularProgressIndicator(Modifier.size(28.dp), color = Kit.Green)
        else RemoteImage(url, "Logo", Modifier.fillMaxSize(), contentScale = ContentScale.Fit, placeholder = empty)
    }
}

/** A small grey chip with an icon: one fact about the restaurant. */
@Composable
private fun FactChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(Kit.Tint).padding(start = 9.dp, end = 11.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, Modifier.size(14.dp), tint = Kit.Muted)
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink, maxLines = 1)
    }
}


/**
 * The page's parts as underlined tabs with icons (the chosen one green). The careful ones (Legal & tax, Currency & time
 * zone) sit apart at the end, with amber icons; on a narrow window everything slides sideways, a line before those two.
 */
@Composable
private fun RestaurantTabs(tabs: List<RestaurantTab>, chosen: RestaurantTab, onTab: (RestaurantTab) -> Unit, wide: Boolean) {
    val main = tabs.filter { !it.careful }
    val careful = tabs.filter { it.careful }
    val scroll = androidx.compose.foundation.rememberScrollState()
    val spans = remember { androidx.compose.runtime.mutableStateMapOf<RestaurantTab, Pair<Int, Int>>() }
    if (!wide) com.saporini.mobile_desktop.core.components.CenterChosen(scroll, chosen, spans)
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(Kit.Border))
        Row(Modifier.fillMaxWidth().then(if (wide) Modifier else Modifier.fadingEdges(scroll).horizontalScroll(scroll)),
            verticalAlignment = Alignment.Bottom) {
            main.forEach { tab -> TabItem(tab, tab == chosen, spans) { onTab(tab) } }
            if (careful.isNotEmpty()) {
                if (wide) Spacer(Modifier.weight(1f))
                Box(Modifier.padding(horizontal = 6.dp, vertical = 12.dp).width(1.dp).height(20.dp).background(Kit.Border))
                careful.forEach { tab ->
                    com.saporini.mobile_desktop.core.ui.HoverTooltip("Careful: changing this affects the whole restaurant") {
                        TabItem(tab, tab == chosen, spans) { onTab(tab) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabItem(tab: RestaurantTab, on: Boolean, spans: MutableMap<RestaurantTab, Pair<Int, Int>>, onClick: () -> Unit) {
    val tint = if (tab.careful) Kit.Amber else Kit.Green
    Column(Modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Max)
        .onGloballyPositioned { spans[tab] = it.positionInParent().x.roundToInt() to it.size.width }
        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(tabIcon(tab), null, Modifier.size(18.dp), tint = if (on || tab.careful) tint else Kit.Muted)
            Text(tab.label, fontFamily = Inter(), fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, fontSize = 14.sp,
                letterSpacing = 0.sp, color = if (on) tint else Kit.Ink, maxLines = 1, softWrap = false)
        }
        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(if (on) tint else Color.Transparent))
    }
}

private fun tabIcon(tab: RestaurantTab) = when (tab) {
    RestaurantTab.DETAILS -> Icons.Outlined.Storefront
    RestaurantTab.BRANCHES -> Icons.Outlined.Business
    RestaurantTab.HOURS -> Icons.Outlined.Schedule
    RestaurantTab.BRANDING -> Icons.Outlined.Receipt
    RestaurantTab.MONEY -> Icons.Outlined.CurrencyExchange
}

// ---- Details ----

/**
 * Details (user, 2026-11-23: "this page looks terrible, and it repeats how guests reach you: just contact"): one form with
 * each thing once (who you are, about, contact with the address of the branch you're in, social links); then, for the
 * Super Admin, the owner, open or closed, and removing the restaurant.
 */
@Composable
private fun DetailsTab(state: RestaurantDetailsState, model: RestaurantDetailsScreenModel, size: ScreenSize, wide: Boolean,
                       here: com.saporini.mobile_desktop.platform.BranchDto?) {
    // No preview of the booking website beside it (user, 2026-11-23: "not realistic, I haven't started the website yet").
    DetailsForm(state, model, here, size, wide)
    if (state.superAdmin) {
        if (wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.Top) {
            OwnerCard(state, model, Modifier.weight(1f), compact = false)
            OpenCloseCard(state, model, Modifier.weight(1f), compact = false)
        } else {
            OwnerCard(state, model, Modifier.fillMaxWidth(), compact = size.isPhone)
            OpenCloseCard(state, model, Modifier.fillMaxWidth(), compact = size.isPhone)
        }
    }
    // Removing the restaurant comes last, on its own (user rule).
    if (state.canDelete) RemoveZone("Remove the restaurant", "It disappears everywhere and its staff are signed out. Its sales and records are kept.",
        "Remove restaurant", compact = size.isPhone, enabled = !state.saving, onRemove = model::askRemoveRestaurant)
}

/** Whether the details form differs from what's saved. */
private fun detailsChanged(state: RestaurantDetailsState): Boolean {
    val d = state.details ?: return false
    val r = state.restaurant ?: return false
    return d.name != r.name || d.legalName != r.legalName || d.code != r.code || d.description != r.description.orEmpty() ||
        d.email != r.email.orEmpty() || d.phone != r.phone.orEmpty() || d.website != r.website.orEmpty() ||
        d.social != com.saporini.mobile_desktop.platform.socialOf(r)
}

/**
 * The details, shown as they are until Edit (user, 2026-11-23: "make it so you need an edit mode, it's confusing"). Two
 * columns on a wide window (user: "who you are and social on the left, contact and the map on the right"), one under the
 * other otherwise. Name, legal name and code are the Super Admin's: an owner sees the first two locked and never the code.
 * The phone and email are the branch's too: changing them here changes them on the branch's page, and the other way round.
 */
@Composable
private fun DetailsForm(state: RestaurantDetailsState, model: RestaurantDetailsScreenModel, here: com.saporini.mobile_desktop.platform.BranchDto?,
                        size: ScreenSize, wide: Boolean) {
    val restaurant = state.restaurant ?: return
    val draft = state.details
    // Each card has its own Edit (user, 2026-11-23); one card is changed at a time.
    val editingCard = state.editingCard.takeIf { state.canEdit && draft != null }
    val on = { card: String -> editingCard == card }
    val trailing = { card: String, extra: (@Composable () -> Unit)? ->
        @Composable {
            when {
                on(card) -> extra?.invoke()
                state.canEdit -> EditPill(enabled = editingCard == null && draft != null && !state.saving) { model.editCard(card) }
            }
        }
    }
    val problems = if (draft?.showProblems == true) detailsProblems(draft) else emptyMap()
    val branchDraft = here?.let { h -> state.branch?.takeIf { it.id == h.id } }
    val placeProblems = if (branchDraft?.showProblems == true) branchProblems(branchDraft) else emptyMap()
    val savedAddress = here?.let { state.branchAddresses[it.id].orEmpty().firstOrNull() }
    val editable = !state.saving
    val placeEditable = editable && branchDraft != null
    val locked = !state.superAdmin
    val compact = size.isPhone
    val pairs = !compact
    val value = { saved: String?, drafted: String? -> drafted ?: saved.orEmpty() }
    // What guests see: the branch's phone and email (kept the same as the restaurant's), else the restaurant's.
    val phone = (branchDraft?.phone ?: here?.phone)?.trim()?.ifBlank { null } ?: value(restaurant.phone, draft?.phone)
    val email = (branchDraft?.email ?: here?.email)?.trim()?.ifBlank { null } ?: value(restaurant.email, draft?.email)
    val street = branchDraft?.street ?: savedAddress?.streetLine1.orEmpty()
    val postalCode = branchDraft?.postalCode ?: savedAddress?.postalCode.orEmpty()
    val city = branchDraft?.city ?: savedAddress?.city.orEmpty()
    val country = branchDraft?.country ?: savedAddress?.country.orEmpty()
    val social = draft?.social ?: com.saporini.mobile_desktop.platform.socialOf(restaurant)
    val changeSocial = { f: (com.saporini.mobile_desktop.platform.SocialLinks) -> com.saporini.mobile_desktop.platform.SocialLinks ->
        model.changeDetails { it.copy(social = f(it.social)) }
    }
    val who: @Composable (Modifier) -> Unit = { m ->
        InfoSection("Who you are", "Your name, and a few words guests read about you.", m, picture = Res.drawable.workspace_restaurants,
            compact = compact, marked = on("who"),
            trailing = trailing("who", if (locked) ({ LockedNote("Name: set by the Super Admin") }) else null)) {
            if (on("who")) {
                FieldPair(
                    { f -> TextInput(value(restaurant.name, draft?.name), { v -> model.changeDetails { it.copy(name = v) } }, f, label = "Name",
                        required = !locked, icon = Icons.Outlined.Storefront, problem = problems["name"], maxLength = MAX_RESTAURANT_NAME,
                        enabled = editable && !locked) },
                    { f -> TextInput(value(restaurant.legalName, draft?.legalName), { v -> model.changeDetails { it.copy(legalName = v) } }, f,
                        label = "Legal name", required = !locked, icon = Icons.Outlined.Gavel, problem = problems["legalName"], maxLength = MAX_LEGAL_NAME,
                        enabled = editable && !locked, hint = "As registered, e.g. on invoices") },
                    wide = pairs
                )
                if (state.superAdmin) TextInput(value(restaurant.code, draft?.code), { v -> model.changeDetails { it.copy(code = v) } }, Modifier.fillMaxWidth(),
                    label = "Code", required = true, icon = Icons.Outlined.Badge, problem = problems["code"], maxLength = MAX_CODE, enabled = editable,
                    hint = "A short name the system uses inside, e.g. SAPORINI_TIRANA. Guests never see it.")
                TextInput(value(restaurant.description, draft?.description), { v -> model.changeDetails { it.copy(description = v) } }, Modifier.fillMaxWidth(),
                    label = "Description", optional = true, singleLine = false, minLines = 4, problem = problems["description"], maxLength = MAX_DESCRIPTION,
                    enabled = editable, placeholder = "A family trattoria in the heart of Tirana: fresh pasta every day and a garden for the summer.")
                CardButtons(state, model)
            } else {
                ValueLine(Icons.Outlined.Storefront, "Name", value(restaurant.name, draft?.name))
                ValueLine(Icons.Outlined.Gavel, "Legal name", value(restaurant.legalName, draft?.legalName))
                if (state.superAdmin) ValueLine(Icons.Outlined.Badge, "Code", value(restaurant.code, draft?.code))
                ValueLine(Icons.Outlined.Description, "Description", value(restaurant.description, draft?.description), lines = 6)
            }
        }
    }
    val socials: @Composable (Modifier) -> Unit = { m ->
        InfoSection("Social links", "Where guests find you online.", m, picture = Res.drawable.overview_guests, compact = compact,
            marked = on("social"), trailing = trailing("social", null)) {
            if (on("social")) {
                val field: @Composable (SocialNet, Modifier) -> Unit = { net, f ->
                    SocialField(net, social.valueOf(net), { v -> changeSocial { s -> s.with(net, v) } }, problems[net.key], editable, f)
                }
                // Beside the other column each link has its own line; across the whole width they go two a line.
                if (wide || compact) SocialNet.entries.forEach { net -> field(net, Modifier.fillMaxWidth()) }
                else {
                    FieldPair({ f -> field(SocialNet.INSTAGRAM, f) }, { f -> field(SocialNet.FACEBOOK, f) }, wide = true)
                    FieldPair({ f -> field(SocialNet.TIKTOK, f) }, { f -> field(SocialNet.WHATSAPP, f) }, wide = true)
                    field(SocialNet.MAPS, Modifier.fillMaxWidth())
                }
                CardButtons(state, model)
            } else {
                // Every network shows, "Not set" when empty (user, 2026-11-23).
                SocialNet.entries.forEach { net ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SocialBadge(net, 34.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(net.label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = Kit.Muted, translate = false)
                            val link = social.valueOf(net)
                            if (link.isBlank()) Text("Not set", fontFamily = Inter(), fontSize = 13.5.sp, color = Kit.Faint)
                            else Text(link, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 13.5.sp, color = Kit.Ink, maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, translate = false)
                        }
                    }
                }
            }
        }
    }
    val contact: @Composable (Modifier) -> Unit = { m ->
        InfoSection("Contact", "On the booking website and in booking emails. Receipts print the phone and the address.", m,
            picture = Res.drawable.settings_reservations, compact = compact, marked = on("contact"), trailing = trailing("contact", null)) {
            if (on("contact")) {
                // One phone and one email: the restaurant's and this branch's are changed together.
                FieldPair(
                    { f -> TextInput(phone, { v -> model.changeDetails { it.copy(phone = v) }; if (here != null) model.changeBranch { it.copy(phone = v) } }, f,
                        label = "Phone", required = true, icon = Icons.Outlined.Phone, problem = problems["phone"] ?: placeProblems["phone"], enabled = editable,
                        placeholder = "+355 69 123 4567") },
                    { f -> TextInput(email, { v -> model.changeDetails { it.copy(email = v.trim()) }; if (here != null) model.changeBranch { it.copy(email = v.trim()) } },
                        f, label = "Email", required = true, icon = Icons.Outlined.Mail, problem = problems["email"] ?: placeProblems["email"],
                        maxLength = MAX_EMAIL_TEXT, enabled = editable, placeholder = "hello@restaurant.al") },
                    wide = pairs
                )
                TextInput(value(restaurant.website, draft?.website), { v -> model.changeDetails { it.copy(website = v.trim()) } }, Modifier.fillMaxWidth(),
                    label = "Website", optional = true, icon = Icons.Outlined.Language, problem = problems["website"], maxLength = MAX_WEBSITE,
                    enabled = editable, placeholder = "https://")
                if (here != null) {
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Address", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Kit.Ink)
                        if (state.branches.size > 1) Text("· ${here.name}", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted, translate = false)
                    }
                    TextInput(street, { v -> model.changeBranch { it.copy(street = v) } }, Modifier.fillMaxWidth(), label = "Street", icon = Icons.Outlined.Place,
                        problem = placeProblems["street"], maxLength = MAX_STREET, enabled = placeEditable, placeholder = "Rruga e Durrësit 12")
                    val postal: @Composable (Modifier) -> Unit = { f ->
                        TextInput(postalCode, { v -> model.changeBranch { it.copy(postalCode = v) } }, f, label = "Postal code",
                            problem = placeProblems["postalCode"], maxLength = MAX_POSTAL, enabled = placeEditable)
                    }
                    val town: @Composable (Modifier) -> Unit = { f ->
                        TextInput(city, { v -> model.changeBranch { it.copy(city = v) } }, f, label = "City", problem = placeProblems["city"],
                            maxLength = MAX_CITY, enabled = placeEditable)
                    }
                    val land: @Composable (Modifier) -> Unit = { f ->
                        TextInput(country, { v -> model.changeBranch { it.copy(country = v) } }, f, label = "Country", problem = placeProblems["country"],
                            maxLength = MAX_CITY, enabled = placeEditable)
                    }
                    if (compact) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            postal(Modifier.weight(0.8f))
                            town(Modifier.weight(1f))
                        }
                        land(Modifier.fillMaxWidth())
                    } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        postal(Modifier.weight(0.75f))
                        town(Modifier.weight(1f))
                        land(Modifier.weight(1f))
                    }
                }
                CardButtons(state, model)
            } else {
                ValueLine(Icons.Outlined.Phone, "Phone", phone)
                ValueLine(Icons.Outlined.Mail, "Email", email)
                ValueLine(Icons.Outlined.Language, "Website", value(restaurant.website, draft?.website))
                if (here != null) ValueLine(Icons.Outlined.Place, if (state.branches.size > 1) "Address · ${here.name}" else "Address",
                    listOfNotNull(street.trim().ifBlank { null }, listOf(postalCode, city).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
                        .ifBlank { null }, country.trim().ifBlank { null }).joinToString(", "), lines = 2)
            }
        }
    }
    // The branch's exact spot as a pin (the real map comes later, user 2026-11-23: "we will implement the map too").
    val map: @Composable (Modifier) -> Unit = { m ->
        val coordinates = branchDraft?.coordinates ?: savedAddress?.let { a -> a.latitude?.let { lat -> a.longitude?.let { lng ->
            com.saporini.mobile_desktop.platform.coordinatesText(lat to lng) } } }.orEmpty()
        val point = com.saporini.mobile_desktop.platform.parseCoordinates(coordinates)
        val uri = androidx.compose.ui.platform.LocalUriHandler.current
        InfoSection("Map", if (on("map")) "Click on the map to put the pin there, or paste the exact location." else "Your pin, at the exact location.",
            m, picture = Res.drawable.inventory_places, compact = compact, marked = on("map"), trailing = trailing("map", null)) {
            Box(Modifier.fillMaxWidth().height(if (compact) 220.dp else 280.dp).clip(RoundedCornerShape(14.dp))
                .border(1.dp, if (on("map")) Kit.Green.copy(alpha = 0.5f) else Kit.Border, RoundedCornerShape(14.dp))) {
                com.saporini.mobile_desktop.core.ui.TileMap(point, com.saporini.mobile_desktop.core.ui.mapStartFor(restaurant.timezone), Modifier.fillMaxSize(),
                    onPick = if (on("map") && placeEditable) ({ p -> model.changeBranch { it.copy(coordinates = com.saporini.mobile_desktop.platform.coordinatesText(p)) } })
                        else null)
                if (point == null) Row(Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(50)).background(Color.White)
                    .border(1.dp, Kit.Border, RoundedCornerShape(50)).padding(horizontal = 11.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Place, null, Modifier.size(14.dp), tint = Kit.Muted)
                    Text("No pin yet", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted)
                } else Row(Modifier.align(Alignment.BottomStart).padding(10.dp).shadow(2.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50))
                    .background(Color.White).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.MyLocation, null, Modifier.size(14.dp), tint = Kit.Ink)
                    Text(com.saporini.mobile_desktop.platform.coordinatesText(point), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                        color = Kit.Ink, translate = false)
                }
            }
            if (on("map")) {
                ExactLocationField(coordinates, placeProblems["coordinates"], placeEditable) { v -> model.changeBranch { it.copy(coordinates = v) } }
                CardButtons(state, model)
            } else if (point != null) LinkText("Open it in Google Maps ›") {
                runCatching { uri.openUri("https://www.google.com/maps?q=${point.first},${point.second}") }
            } else Text("No pin yet. Use Edit to put it on the map.", fontFamily = Inter(), fontSize = 12.5.sp, color = Kit.Faint)
        }
    }
    val left: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            who(Modifier.fillMaxWidth())
            socials(Modifier.fillMaxWidth())
        }
    }
    val right: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            contact(Modifier.fillMaxWidth())
            if (here != null) map(Modifier.fillMaxWidth())
        }
    }
    if (wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        left(Modifier.weight(1f))
        right(Modifier.weight(1f))
    } else {
        left(Modifier.fillMaxWidth())
        right(Modifier.fillMaxWidth())
    }
}

/** A card's Edit: a small outlined pill with a pencil (faded while another card is being changed). */
@Composable
private fun EditPill(enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.alpha(if (enabled) 1f else 0.45f).clip(RoundedCornerShape(50)).background(if (hovered && enabled) Kit.GreenSoft else Color.White)
            .border(1.dp, if (hovered && enabled) Kit.Green.copy(alpha = 0.5f) else Kit.Border, RoundedCornerShape(50))
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Outlined.Edit, null, Modifier.size(15.dp), tint = Kit.Green)
        Text("Edit", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Kit.Ink)
    }
}

/** At the foot of the card being changed: Cancel (puts it back) and Save. */
@Composable
private fun CardButtons(state: RestaurantDetailsState, model: RestaurantDetailsScreenModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Kit.Border))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            KitButton("Cancel", { model.editCard(null) }, style = ButtonStyle.SECONDARY, enabled = !state.saving)
            KitButton("Save", model::saveDetailsAndBranch, loading = state.saving)
        }
    }
}

/** One detail as it's saved: an icon, its name, its value (or that it's missing). */
@Composable
private fun ValueLine(icon: ImageVector, label: String, value: String?, lines: Int = 1) {
    val text = value?.trim().orEmpty()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Kit.Canvas), Alignment.Center) {
            Icon(icon, null, Modifier.size(17.dp), tint = Kit.Muted)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = Kit.Muted)
            if (text.isEmpty()) Text("Not set", fontFamily = Inter(), fontSize = 13.5.sp, color = Kit.Faint)
            else Text(text, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 13.5.sp, lineHeight = 19.sp, color = Kit.Ink, maxLines = lines,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, translate = false)
        }
    }
}

/** The places guests find a restaurant online, each in its own colours. */
internal enum class SocialNet(val key: String, val label: String, val placeholder: String, val colors: List<Color>,
                              val icon: ImageVector?, val letter: String? = null) {
    INSTAGRAM("instagram", "Instagram", "instagram.com/yourrestaurant", listOf(Color(0xFFF58529), Color(0xFFDD2A7B), Color(0xFF8134AF)),
        Icons.Outlined.PhotoCamera),
    FACEBOOK("facebook", "Facebook", "facebook.com/yourrestaurant", listOf(Color(0xFF1877F2)), null, "f"),
    TIKTOK("tiktok", "TikTok", "@yourrestaurant", listOf(Color(0xFF111111)), Icons.Outlined.MusicNote),
    WHATSAPP("whatsapp", "WhatsApp", "+355 69 123 4567", listOf(Color(0xFF25D366)), Icons.AutoMirrored.Outlined.Chat),
    MAPS("maps", "Google Maps", "maps.app.goo.gl/…", listOf(Color(0xFFEA4335)), Icons.Outlined.Place)
}

private fun com.saporini.mobile_desktop.platform.SocialLinks.valueOf(net: SocialNet) = when (net) {
    SocialNet.INSTAGRAM -> instagram
    SocialNet.FACEBOOK -> facebook
    SocialNet.TIKTOK -> tiktok
    SocialNet.WHATSAPP -> whatsapp
    SocialNet.MAPS -> maps
}

private fun com.saporini.mobile_desktop.platform.SocialLinks.with(net: SocialNet, value: String) = when (net) {
    SocialNet.INSTAGRAM -> copy(instagram = value.trim())
    SocialNet.FACEBOOK -> copy(facebook = value.trim())
    SocialNet.TIKTOK -> copy(tiktok = value.trim())
    SocialNet.WHATSAPP -> copy(whatsapp = value)
    SocialNet.MAPS -> copy(maps = value.trim())
}

/** A social network's square, in its colours. */
@Composable
internal fun SocialBadge(net: SocialNet, size: androidx.compose.ui.unit.Dp) {
    val shape = RoundedCornerShape(size * 0.28f)
    Box(Modifier.size(size).clip(shape).then(if (net.colors.size == 1) Modifier.background(net.colors.first()) else Modifier.background(Brush.linearGradient(net.colors))),
        Alignment.Center) {
        if (net.letter != null) Text(net.letter, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = (size.value * 0.5f).sp, color = Color.White,
            translate = false)
        else net.icon?.let { Icon(it, null, Modifier.size(size * 0.5f), tint = Color.White) }
    }
}

@Composable
private fun SocialField(net: SocialNet, value: String, onChange: (String) -> Unit, problem: String?, enabled: Boolean, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(net.label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, translate = false)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SocialBadge(net, 46.dp)
            TextInput(value, onChange, Modifier.weight(1f), placeholder = net.placeholder, problem = problem,
                maxLength = com.saporini.mobile_desktop.platform.MAX_SOCIAL_LINK, enabled = enabled)
        }
    }
}

/** The owner (Super Admin): who they are, whether they signed up, the invitation, giving the restaurant to someone else. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun OwnerCard(state: RestaurantDetailsState, model: RestaurantDetailsScreenModel, modifier: Modifier, compact: Boolean) {
    InfoSection("Owner", "Who owns the restaurant and runs it in the POS.", modifier, picture = Res.drawable.users_owner, compact = compact) {
        val owner = state.owner
        if (owner == null) {
            Text(if (state.restaurant?.ownerUserId == null) "No owner yet. Add the person who owns the restaurant; they get an email to set a password."
                else "The owner's account couldn't be found.", fontFamily = Inter(), fontSize = 13.sp, lineHeight = 18.sp, color = Kit.Muted)
            if (state.restaurant?.ownerUserId == null && state.canManage) {
                KitButton("Add the owner", model::startAddOwner, icon = Icons.Outlined.PersonAdd, enabled = !state.saving)
            }
        } else {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Kit.Canvas).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InitialsFace(owner.displayName, 44.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(owner.displayName, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = Kit.Ink, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, translate = false)
                    Text(owner.email, fontFamily = Inter(), fontSize = 12.5.sp, color = Kit.Muted, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, translate = false)
                }
                if (owner.emailVerified) DotPill("Signed up", Kit.Green) else DotPill("Not signed up yet", Kit.Amber)
            }
            if (!owner.emailVerified) InviteNote(state.invite)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!owner.emailVerified && state.canManage) KitButton("Send the invitation again", model::resendOwnerInvite,
                    icon = Icons.AutoMirrored.Outlined.Send, style = ButtonStyle.SECONDARY, enabled = !state.saving)
                if (state.canTransferOwner) KitButton("Transfer ownership", model::startTransfer, icon = Icons.Outlined.SwapHoriz,
                    style = ButtonStyle.SECONDARY, enabled = !state.saving)
            }
        }
        OwnerHistory(state.ownerTransfers)
    }
}

/** Open, suspended or closed (Super Admin), in its colour, with the buttons to change it. */
@Composable
private fun OpenCloseCard(state: RestaurantDetailsState, model: RestaurantDetailsScreenModel, modifier: Modifier, compact: Boolean) {
    val restaurant = state.restaurant ?: return
    val color = if (restaurant.open) Kit.Green else Kit.Amber
    InfoSection("Open or closed", "Closing signs its staff out at once. Nothing is deleted.", modifier, icon = Icons.Outlined.Storefront, compact = compact) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.07f)).border(1.dp, color.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.White), Alignment.Center) {
                Icon(if (restaurant.open) Icons.Outlined.CheckCircle else Icons.Outlined.Pause, null, Modifier.size(22.dp), tint = color)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(restaurantStatusLabel(restaurant.status, restaurant.isActive), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
                Text(if (restaurant.open) "Its staff sign in and work as usual." else "Its staff can't sign in until it opens again.",
                    fontFamily = Inter(), fontSize = 12.5.sp, lineHeight = 17.sp, color = Kit.Ink)
            }
        }
        if (state.canManage) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (restaurant.open) {
                KitButton("Suspend", { model.askStatus(false, "SUSPENDED") }, icon = Icons.Outlined.Pause, style = ButtonStyle.SECONDARY, enabled = !state.saving)
                KitButton("Close", { model.askStatus(false, "INACTIVE") }, icon = Icons.Outlined.Block, style = ButtonStyle.SECONDARY, enabled = !state.saving)
            } else {
                KitButton("Open again", { model.askStatus(true, "ACTIVE") }, icon = Icons.Outlined.PlayArrow, enabled = !state.saving)
            }
        }
    }
}

/** One thing in a section's list: a picture or face, a small line over the title, the title, a detail, "Main", ⋮. */
@Composable
private fun ListLine(
    leading: @Composable () -> Unit,
    title: String,
    over: String?,
    detail: String?,
    main: Boolean,
    actions: List<RowAction>,
    busy: Boolean,
    onClick: (() -> Unit)?
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, Kit.RowBorder, RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        leading()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            over?.takeIf { it.isNotBlank() }?.let { Text(it, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = Kit.Muted, maxLines = 1) }
            Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.5.sp, lineHeight = 18.sp, color = Kit.Ink, maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, translate = false)
            detail?.let { Text(it, fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, translate = false) }
        }
        if (main) DotPill("Main", Kit.Green)
        RowActionsMenu(actions, busy)
    }
}

@Composable
private fun LineTile(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Kit.GreenSoft), Alignment.Center) {
        Icon(icon, null, Modifier.size(20.dp), tint = Kit.Green)
    }
}

// ---- Logo & receipts ----

/** Saves the receipt: its design (on this device for now) and, when they changed, the top and bottom texts. */
private fun saveReceipt(state: RestaurantDetailsState, model: RestaurantDetailsScreenModel) = model.saveReceipt()

/** Puts the receipt back as it was saved. */
private fun discardReceipt(state: RestaurantDetailsState, model: RestaurantDetailsScreenModel) = model.discardReceipt()

/** The receipt, and only the receipt (user, 2026-11-23: the logo is changed at the top of the page). */
@Composable
private fun BrandingTab(state: RestaurantDetailsState, model: RestaurantDetailsScreenModel, size: ScreenSize, wide: Boolean) {
    ReceiptDesigner(state, model, wide, size.isPhone)
}

// ---- Currency & time zone (careful) ----

@Composable
private fun MoneyTab(state: RestaurantDetailsState, model: RestaurantDetailsScreenModel, size: ScreenSize, wide: Boolean) {
    val restaurant = state.restaurant ?: return
    val compact = size.isPhone
    CarefulBanner("Careful: these change the whole restaurant",
        "A new currency converts every price, wage and cost; a new time zone moves bookings, opening hours and reports. Each one shows you everything first.",
        compact)
    val currency: @Composable (Modifier) -> Unit = { m ->
        InfoSection("Currency", "Every price, wage, cost and fee is in it.", m, picture = Res.drawable.inventory_stock_value, compact = compact) {
            BigValue(restaurant.currency, currencyName(restaurant.currency))
            Text("Changing it converts every amount at the rate you enter, and you see each one first. Past sales keep their own currency.",
                fontFamily = Inter(), fontSize = 12.5.sp, lineHeight = 18.sp, color = Kit.Muted)
            if (state.canManage) KitButton("Change currency", model::startCurrencyChange, icon = Icons.Outlined.CurrencyExchange,
                style = ButtonStyle.SECONDARY, enabled = !state.saving)
            if (state.currencyChanges.isNotEmpty()) {
                GroupLabel("Earlier changes")
                state.currencyChanges.forEach { change ->
                    ListLine(
                        leading = { LineTile(Icons.Outlined.History) },
                        title = "${change.oldCurrency} → ${change.newCurrency} · 1 ${change.oldCurrency} = ${change.rate.value} ${change.newCurrency}",
                        over = dateTimeText(change.changedAt, TimeZone.of(restaurant.timezone)),
                        detail = listOfNotNull(change.changedByName, "${change.valuesConverted} amounts").joinToString(" · "),
                        main = false, actions = emptyList(), busy = false, onClick = null
                    )
                }
            }
        }
    }
    val zone: @Composable (Modifier) -> Unit = { m ->
        InfoSection("Time zone", "Opening hours, bookings and reports follow it.", m, picture = Res.drawable.overview_next_hours, compact = compact) {
            val now = runCatching { kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.of(restaurant.timezone)).time }.getOrNull()
            val clock = now?.let { "${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}" }
            BigValue(timeZoneLabel(restaurant.timezone), listOfNotNull(restaurant.timezone, clock?.let { "$it there now" }).joinToString(" · "))
            Text("Before changing it you see which bookings and times move.", fontFamily = Inter(), fontSize = 12.5.sp, lineHeight = 18.sp, color = Kit.Muted)
            if (state.canManage) KitButton("Change time zone", model::startTimezoneChange, icon = Icons.Outlined.Language,
                style = ButtonStyle.SECONDARY, enabled = !state.saving)
        }
    }
    if (wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        currency(Modifier.weight(1f))
        zone(Modifier.weight(1f))
    } else {
        currency(Modifier.fillMaxWidth())
        zone(Modifier.fillMaxWidth())
    }
}

@Composable
private fun BigValue(value: String, detail: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Kit.Canvas).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.3).sp, color = Kit.Ink, translate = false)
        Text(detail, fontFamily = Inter(), fontSize = 12.5.sp, color = Kit.Muted)
    }
}

// ---- Dialogs ----

@Composable
private fun Dialogs(state: RestaurantDetailsState, model: RestaurantDetailsScreenModel) {
    // Adding a branch is a window; a branch's own page changes it in place.
    state.branch?.takeIf { it.id == null }?.let { BranchDialog(it, state, model) }
    state.address?.let { AddressDialog(it, state, model) }
    state.contact?.let { ContactDialog(it, state, model) }
    state.tax?.let { TaxDialog(it, state, model) }
    state.currency?.let { CurrencyDialog(it, state, model) }
    state.timezone?.let { TimezoneDialog(it, state, model) }
    state.confirm?.let { ConfirmDialogs(it, state, model) }
    state.ownerStep?.let { OwnerDialog(it, model::editOwner, model::saveOwner, model::cancelOwner) }
    state.transfer?.let { OwnerTransferDialog(it, model) }
}

/**
 * Tax details' and company addresses' windows (add, change, remove), for Finance & taxation → Tax setup (user, 2026-11-23:
 * taxes moved there, and the company's addresses with them).
 */
@Composable
internal fun CompanyDialogs(state: RestaurantDetailsState, model: RestaurantDetailsScreenModel) {
    state.tax?.let { TaxDialog(it, state, model) }
    state.address?.let { AddressDialog(it, state, model) }
    state.confirm?.takeIf { it is RestaurantConfirm.RemoveTax || it is RestaurantConfirm.RemoveAddress }?.let { ConfirmDialogs(it, state, model) }
}

@Composable
private fun DialogButtons(state: RestaurantDetailsState, onCancel: () -> Unit, onSave: () -> Unit, saveText: String = "Save") {
    KitButton("Cancel", onCancel, style = ButtonStyle.SECONDARY, enabled = !state.saving)
    KitButton(saveText, onSave, loading = state.saving)
}

@Composable
private fun DetailsDialog(draft: DetailsDraft, state: RestaurantDetailsState, model: RestaurantDetailsScreenModel) {
    val problems = if (draft.showProblems) detailsProblems(draft) else emptyMap()
    val locked = !state.superAdmin
    AppDialog("Edit details", model::cancelDetails, busy = state.saving, maxWidth = 680.dp,
        subtitle = if (locked) "The name, legal name and code are changed by the Super Admin" else null,
        buttons = { DialogButtons(state, model::cancelDetails, model::saveDetails) }) {
        state.error?.let { MessageBar(it, MessageKind.ERROR) }
        FieldPair({ m -> TextInput(draft.name, { v -> model.changeDetails { it.copy(name = v) } }, m, label = "Name", required = true,
            problem = problems["name"], maxLength = MAX_RESTAURANT_NAME, enabled = !locked) },
            { m -> TextInput(draft.legalName, { v -> model.changeDetails { it.copy(legalName = v) } }, m, label = "Legal name", required = true,
                problem = problems["legalName"], maxLength = MAX_LEGAL_NAME, enabled = !locked) })
        TextInput(draft.code, { v -> model.changeDetails { it.copy(code = v) } }, Modifier.fillMaxWidth(), label = "Code", required = true,
            problem = problems["code"], maxLength = MAX_CODE, enabled = !locked, hint = "A short name for the restaurant, e.g. SAPORINI_TIRANA")
        TextInput(draft.description, { v -> model.changeDetails { it.copy(description = v) } }, Modifier.fillMaxWidth(), label = "Description",
            optional = true, singleLine = false, minLines = 3, problem = problems["description"], maxLength = MAX_DESCRIPTION)
        FieldPair({ m -> TextInput(draft.email, { v -> model.changeDetails { it.copy(email = v) } }, m, label = "Email", required = true,
            icon = Icons.Outlined.Mail, problem = problems["email"], maxLength = MAX_EMAIL_TEXT, hint = "Guests see it on the website and in their emails") },
            { m -> TextInput(draft.phone, { v -> model.changeDetails { it.copy(phone = v) } }, m, label = "Phone", required = true,
                icon = Icons.Outlined.Phone, problem = problems["phone"], hint = "Guests see it on the website and in their emails") })
        TextInput(draft.website, { v -> model.changeDetails { it.copy(website = v) } }, Modifier.fillMaxWidth(), label = "Website", optional = true,
            icon = Icons.Outlined.Language, problem = problems["website"], maxLength = MAX_WEBSITE, placeholder = "https://")
    }
}

@Composable
private fun BranchDialog(draft: BranchDraft, state: RestaurantDetailsState, model: RestaurantDetailsScreenModel) {
    BranchFormDialog(
        title = if (draft.id == null) "Add a branch" else "Edit ${draft.name.ifBlank { "branch" }}",
        draft = draft, saving = state.saving, error = state.error,
        onChange = model::changeBranch, onSave = model::saveBranch, onCancel = model::cancelBranch,
        saveText = if (draft.id == null) "Add branch" else "Save",
        subtitle = if (!state.superAdmin) "The branch's name and code are changed by the Super Admin" else null,
        locked = !state.superAdmin
    )
}

@Composable
private fun AddressDialog(draft: AddressDraft, state: RestaurantDetailsState, model: RestaurantDetailsScreenModel) {
    val problems = if (draft.showProblems) addressProblems(draft) else emptyMap()
    AppDialog(if (draft.id == null) "Add an address" else "Edit address", model::cancelAddress, busy = state.saving, maxWidth = 640.dp,
        buttons = { DialogButtons(state, model::cancelAddress, model::saveAddress) }) {
        state.error?.let { MessageBar(it, MessageKind.ERROR) }
        SelectInput(draft.type, ADDRESS_TYPES, ::addressTypeLabel, { t -> model.changeAddress { it.copy(type = t) } }, Modifier.fillMaxWidth(), label = "What it is")
        TextInput(draft.street, { v -> model.changeAddress { it.copy(street = v) } }, Modifier.fillMaxWidth(), label = "Street", required = true,
            icon = Icons.Outlined.Place, problem = problems["street"], maxLength = MAX_STREET)
        TextInput(draft.street2, { v -> model.changeAddress { it.copy(street2 = v) } }, Modifier.fillMaxWidth(), label = "Building, floor", optional = true,
            problem = problems["street2"], maxLength = MAX_STREET)
        val postal: @Composable (Modifier) -> Unit = { m ->
            TextInput(draft.postalCode, { v -> model.changeAddress { it.copy(postalCode = v) } }, m, label = "Postal code",
                problem = problems["postalCode"], maxLength = MAX_POSTAL)
        }
        val city: @Composable (Modifier) -> Unit = { m ->
            TextInput(draft.city, { v -> model.changeAddress { it.copy(city = v) } }, m, label = "City", required = true,
                problem = problems["city"], maxLength = MAX_CITY)
        }
        val country: @Composable (Modifier) -> Unit = { m ->
            TextInput(draft.country, { v -> model.changeAddress { it.copy(country = v) } }, m, label = "Country", required = true,
                problem = problems["country"], maxLength = MAX_CITY)
        }
        // Three a row; on a phone the postal code and city share one and the country gets its own.
        if (com.saporini.mobile_desktop.core.components.LocalNarrowDialog.current) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                postal(Modifier.weight(0.75f))
                city(Modifier.weight(1f))
            }
            country(Modifier.fillMaxWidth())
        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            postal(Modifier.weight(0.6f))
            city(Modifier.weight(1f))
            country(Modifier.weight(1f))
        }
        ToggleRow("Main address", draft.primary, { v -> model.changeAddress { it.copy(primary = v) } },
            detail = "Shown on the restaurant list and printed on receipts of branches without their own address")
    }
}

@Composable
private fun ContactDialog(draft: ContactDraft, state: RestaurantDetailsState, model: RestaurantDetailsScreenModel) {
    val problems = if (draft.showProblems) contactProblems(draft) else emptyMap()
    AppDialog(if (draft.id == null) "Add a contact" else "Edit contact", model::cancelContact, busy = state.saving, maxWidth = 640.dp,
        buttons = { DialogButtons(state, model::cancelContact, model::saveContact) }) {
        state.error?.let { MessageBar(it, MessageKind.ERROR) }
        FieldPair({ m -> TextInput(draft.fullName, { v -> model.changeContact { it.copy(fullName = v) } }, m, label = "Name", required = true,
            icon = Icons.Outlined.Person, problem = problems["fullName"], maxLength = 150) },
            { m -> SelectInput(draft.type, CONTACT_TYPES, ::contactTypeLabel, { t -> model.changeContact { it.copy(type = t) } }, m, label = "For") })
        TextInput(draft.jobTitle, { v -> model.changeContact { it.copy(jobTitle = v) } }, Modifier.fillMaxWidth(), label = "Job title", optional = true,
            problem = problems["jobTitle"], maxLength = 100)
        FieldPair({ m -> TextInput(draft.email, { v -> model.changeContact { it.copy(email = v) } }, m, label = "Email",
            icon = Icons.Outlined.Mail, problem = problems["email"], maxLength = MAX_EMAIL_TEXT) },
            { m -> TextInput(draft.phone, { v -> model.changeContact { it.copy(phone = v) } }, m, label = "Phone",
                icon = Icons.Outlined.Phone, problem = problems["phone"], maxLength = 50) })
        ToggleRow("Main contact", draft.primary, { v -> model.changeContact { it.copy(primary = v) } })
    }
}

@Composable
private fun TaxDialog(draft: TaxDraft, state: RestaurantDetailsState, model: RestaurantDetailsScreenModel) {
    val problems = if (draft.showProblems) taxProblems(draft) else emptyMap()
    AppDialog(if (draft.id == null) "Add tax details" else "Edit tax details", model::cancelTax, busy = state.saving, maxWidth = 640.dp,
        buttons = { DialogButtons(state, model::cancelTax, model::saveTax) }) {
        state.error?.let { MessageBar(it, MessageKind.ERROR) }
        TextInput(draft.country, { v -> model.changeTax { it.copy(country = v) } }, Modifier.fillMaxWidth(), label = "Country", required = true,
            problem = problems["country"], maxLength = 100)
        FieldPair({ m -> TextInput(draft.vatNumber, { v -> model.changeTax { it.copy(vatNumber = v) } }, m, label = "VAT number",
            problem = problems["vatNumber"], maxLength = MAX_TAX_FIELD, placeholder = "e.g. L91234567A") },
            { m -> TextInput(draft.taxNumber, { v -> model.changeTax { it.copy(taxNumber = v) } }, m, label = "Tax number", optional = true,
                problem = problems["taxNumber"], maxLength = MAX_TAX_FIELD) })
        FieldPair({ m -> TextInput(draft.fiscalCode, { v -> model.changeTax { it.copy(fiscalCode = v) } }, m, label = "Fiscal code", optional = true,
            problem = problems["fiscalCode"], maxLength = MAX_TAX_FIELD) },
            { m -> TextInput(draft.taxOffice, { v -> model.changeTax { it.copy(taxOffice = v) } }, m, label = "Tax office", optional = true,
                problem = problems["taxOffice"], maxLength = 150) })
        TextInput(draft.effectiveFrom, { v -> model.changeTax { it.copy(effectiveFrom = v) } }, Modifier.fillMaxWidth(),
            label = "Valid from", optional = true, placeholder = "2026-10-13T00:00:00+02:00", problem = problems["effectiveFrom"], maxLength = 40)
        TextInput(draft.effectiveTo, { v -> model.changeTax { it.copy(effectiveTo = v) } }, Modifier.fillMaxWidth(),
            label = "Valid until (excluded)", optional = true, placeholder = "2027-01-01T00:00:00+01:00", problem = problems["effectiveTo"], maxLength = 40)
        Text("Include the time-zone offset. Leave either date empty for no limit.", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
        ToggleRow("Print on receipts", draft.isDefault, { v -> model.changeTax { it.copy(isDefault = v) } },
            detail = "The main tax details; only one can be printed")
    }
}

@Composable
private fun CurrencyDialog(flow: CurrencyFlow, state: RestaurantDetailsState, model: RestaurantDetailsScreenModel) =
    com.saporini.mobile_desktop.core.money.RealMoney { CurrencyDialogRealMoney(flow = flow, state = state, model = model) }

@Composable
private fun CurrencyDialogRealMoney(flow: CurrencyFlow, state: RestaurantDetailsState, model: RestaurantDetailsScreenModel) {
    val restaurant = state.restaurant ?: return
    val preview = flow.preview
    val choices = CURRENCIES.filter { it.code != restaurant.currency }
    AppDialog(
        "Change currency", model::cancelCurrencyChange, busy = flow.saving, maxWidth = 860.dp,
        subtitle = "From ${restaurant.currency} (${currencyName(restaurant.currency)})",
        buttons = {
            KitButton("Cancel", model::cancelCurrencyChange, style = ButtonStyle.SECONDARY, enabled = !flow.saving)
            if (preview == null) KitButton("See the new prices", model::previewCurrency, loading = flow.loading)
            else KitButton("Change to ${preview.newCurrency}", model::applyCurrencyChange, loading = flow.saving, enabled = preview.canChange)
        }
    ) {
        flow.error?.let { MessageBar(it, MessageKind.ERROR) }
        val narrow = com.saporini.mobile_desktop.core.components.LocalNarrowDialog.current
        val newCurrency: @Composable (Modifier) -> Unit = { m ->
            SearchableSelect(choices.firstOrNull { it.code == flow.newCurrency }, choices, { it.label }, { c -> model.changeCurrencyFlow { it.copy(newCurrency = c.code) } },
                m, label = "New currency", required = true)
        }
        val rate: @Composable (Modifier) -> Unit = { m ->
            TextInput(flow.rateText, { v -> model.changeCurrencyFlow { it.copy(rateText = v) } }, m,
                label = "1 ${restaurant.currency} =", required = true, suffix = flow.newCurrency.ifBlank { null }, placeholder = "e.g. 100.5",
                problem = if (flow.rateText.isNotBlank() && flow.rate == null) "A number above 0, up to 8 decimals" else null,
                hint = if (flow.rateAuto) (flow.rateDate?.let { "Today's rate · $it. You can change it." } ?: "Today's rate. You can change it.") else null)
        }
        val rounding: @Composable (Modifier) -> Unit = { m ->
            SelectInput(flow.step, ROUNDING_STEPS, { "Round to $it" }, { s -> model.changeCurrencyFlow { it.copy(step = s) } }, m,
                label = "Prices", hint = "Stock costs keep 4 decimals")
        }
        // Side by side; one under the other on a phone.
        if (narrow) {
            newCurrency(Modifier.fillMaxWidth())
            rate(Modifier.fillMaxWidth())
            rounding(Modifier.fillMaxWidth())
        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            newCurrency(Modifier.weight(1.2f))
            rate(Modifier.weight(1f))
            rounding(Modifier.weight(0.9f))
        }
        if (preview == null) {
            NoteBox(Icons.Outlined.Info, "Enter the rate you want, not a live one: 1 ${restaurant.currency} is worth how much in the new currency. " +
                "Next you see every amount converted and can correct any of them before anything changes.")
            return@AppDialog
        }
        if (preview.blockers.isNotEmpty()) {
            ReasonList("Settle these first (they're priced in ${preview.currentCurrency})", preview.blockers, Kit.Danger)
        }
        NoteBox(Icons.Outlined.History, "Past orders, payments and booking money keep ${preview.currentCurrency}; nothing already sold is rewritten. " +
            "${preview.recipesFollowing} recipe costs follow the stock costs. Type over any new amount to correct it.")
        preview.groups.forEach { group ->
            GroupLabel(group.title, group.hint)
            group.items.forEach { item ->
                val corrected = flow.corrections[item.key]
                val before: @Composable (Modifier) -> Unit = { m ->
                    Text("${item.oldValue.value} ${preview.currentCurrency}", m, fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted,
                        textAlign = TextAlign.End, maxLines = 1)
                }
                val after: @Composable (Modifier) -> Unit = { m ->
                    TextInput(corrected ?: item.newValue.value, { v -> model.correctValue(item.key, v) }, m, suffix = preview.newCurrency, problem = item.problem)
                }
                if (narrow) {
                    // A phone: the name on its own line, then the old amount → the new one.
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        CellText(item.name, item.detail)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            before(Modifier.weight(0.8f))
                            Text("→", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
                            after(Modifier.weight(1f))
                        }
                    }
                } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CellText(item.name, item.detail, Modifier.weight(1.4f))
                    before(Modifier.weight(0.7f))
                    Text("→", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
                    after(Modifier.weight(0.8f))
                }
            }
        }
        if (preview.groups.isEmpty()) Text("No prices or amounts to convert yet.", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted)
    }
}

@Composable
private fun TimezoneDialog(flow: TimezoneFlow, state: RestaurantDetailsState, model: RestaurantDetailsScreenModel) {
    val restaurant = state.restaurant ?: return
    val preview = flow.preview
    AppDialog(
        "Change time zone", model::cancelTimezoneChange, busy = flow.saving, maxWidth = 760.dp,
        subtitle = "Now ${timeZoneLabel(restaurant.timezone)} (${restaurant.timezone})",
        buttons = {
            KitButton("Cancel", model::cancelTimezoneChange, style = ButtonStyle.SECONDARY, enabled = !flow.saving)
            if (state.canManage) KitButton("Change time zone", model::applyTimezoneChange, loading = flow.saving,
                enabled = preview != null && (!preview.needsConfirmation || flow.confirmed))
        }
    ) {
        flow.error?.let { MessageBar(it, MessageKind.ERROR) }
        SearchableSelect(flow.zone.ifBlank { null }, TIME_ZONES.filter { it != restaurant.timezone }, ::timeZoneLabel, model::chooseZone,
            Modifier.fillMaxWidth(), label = "New time zone", required = true, detailOf = { it })
        if (flow.loading) Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Kit.Green) }
        if (preview == null) return@AppDialog
        val difference = preview.differenceMinutes
        Text(
            when {
                difference == 0 -> "${timeZoneLabel(preview.newTimezone)} is on the same clock right now."
                difference > 0 -> "${timeZoneLabel(preview.newTimezone)} is ${hoursText(difference)} ahead right now."
                else -> "${timeZoneLabel(preview.newTimezone)} is ${hoursText(-difference)} behind right now."
            },
            fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink
        )
        preview.notes.forEach { NoteBox(Icons.Outlined.Info, it) }
        if (preview.openOrders > 0 || preview.openShifts > 0) {
            ReasonList("Still going on", listOfNotNull(
                preview.openOrders.takeIf { it > 0 }?.let { "$it open orders keep their times" },
                preview.openShifts.takeIf { it > 0 }?.let { "$it people clocked in keep their clock-in time" }))
        }
        if (preview.upcomingBookings > 0) {
            GroupLabel("${preview.upcomingBookings} bookings still to come")
            CheckRow("Keep the times guests were told", if (flow.keepBookingTimes) CheckState.ON else CheckState.OFF,
                { model.changeTimezoneFlow { it.copy(keepBookingTimes = !it.keepBookingTimes) } },
                detail = if (flow.keepBookingTimes) "A booking at 20:00 stays at 20:00 in the new time zone" else "Bookings keep their moment, so their clock time moves")
            preview.examples.forEach { example ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CellText(example.name ?: "Booking", example.code, Modifier.weight(1f))
                    Text(example.before, fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
                    Text("→", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
                    Text(if (flow.keepBookingTimes) example.afterKeepingClock else example.afterKeepingMoment, fontFamily = Inter(),
                        fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Kit.Ink)
                }
            }
            CheckRow("I read what moves", if (flow.confirmed) CheckState.ON else CheckState.OFF,
                { model.changeTimezoneFlow { it.copy(confirmed = !it.confirmed, error = null) } }, strong = true)
        }
    }
}

private fun hoursText(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return listOfNotNull(hours.takeIf { it > 0 }?.let { if (it == 1) "1 hour" else "$it hours" }, rest.takeIf { it > 0 }?.let { "$it min" }).joinToString(" ")
}

@Composable
private fun ConfirmDialogs(confirm: RestaurantConfirm, state: RestaurantDetailsState, model: RestaurantDetailsScreenModel) {
    val restaurant = state.restaurant
    val errorExtra: @Composable ColumnScope.() -> Unit = { state.error?.let { MessageBar(it, MessageKind.ERROR) } }
    when (confirm) {
        is RestaurantConfirm.Status -> ConfirmDialog(
            title = if (confirm.open) "Open ${restaurant?.name} again?" else if (confirm.status == "SUSPENDED") "Suspend ${restaurant?.name}?" else "Close ${restaurant?.name}?",
            message = if (confirm.open) "Its staff can sign in and work again." else
                "Its staff are signed out at once and can't sign in until you open it again. Nothing is deleted.",
            confirmText = if (confirm.open) "Open again" else if (confirm.status == "SUSPENDED") "Suspend" else "Close",
            onConfirm = model::confirm, onDismiss = model::dismissConfirm, danger = !confirm.open, busy = state.saving, extra = errorExtra
        )
        RestaurantConfirm.RemoveRestaurant -> RemoveRestaurantDialog(state, model)
        is RestaurantConfirm.RemoveBranch -> {
            val plan = confirm.check?.archive
            val ready = plan?.canRemove == true && confirm.staffAction != null && confirm.confirmedName.trim() == confirm.branch.name
            AppDialog("Remove ${confirm.branch.name}?", model::dismissConfirm, busy = state.saving, maxWidth = 580.dp, buttons = {
                KitButton("Cancel", model::dismissConfirm, style = ButtonStyle.SECONDARY, enabled = !state.saving)
                KitButton("Remove branch", model::confirm, style = ButtonStyle.DANGER, loading = state.saving, enabled = ready)
            }) {
                errorExtra()
                if (state.error != null || (confirm.check != null && plan == null)) {
                    KitButton("Review again", { model.askRemoveBranch(confirm.branch) }, style = ButtonStyle.SECONDARY, enabled = !state.saving)
                }
                when {
                    confirm.check == null -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Kit.Green) }
                    plan == null -> Text("Removal options could not be loaded. Update the server and try again.", color = Kit.Danger)
                    else -> {
                        Text("The branch disappears from the lists. Its sales, payments and historical records stay. Shared menus and stock items stay with the restaurant.",
                            fontFamily = Inter(), fontSize = 14.sp, lineHeight = 20.sp, color = Kit.Ink)
                        Text("Setup to retire: ${plan.devices} devices, ${plan.tables} tables, ${plan.kitchenStations} kitchen stations and ${plan.stockLocations} empty stock locations.",
                            fontFamily = Inter(), fontSize = 13.sp, color = Kit.Ink)
                        if (!plan.canRemove) ReasonList("Finish or move these first", plan.reasons, Kit.Danger)
                        Text("${plan.staff} assigned staff accounts", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Kit.Ink)
                        CheckRow("Remove branch, keep staff for reassignment", if (confirm.staffAction == "KEEP") CheckState.ON else CheckState.OFF,
                            { model.changeBranchRemoval(staffAction = "KEEP") }, strong = true)
                        Text("Their accounts stay. They need an open branch assigned before using POS or KDS.", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Ink)
                        if (plan.canDeactivateStaff) {
                            CheckRow("Remove branch and deactivate its staff", if (confirm.staffAction == "DEACTIVATE") CheckState.ON else CheckState.OFF,
                                { model.changeBranchRemoval(staffAction = "DEACTIVATE") }, strong = true)
                            Text("${plan.staff - plan.protectedStaff} staff accounts become inactive and their sessions end. An administrator can reactivate them later.", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Ink)
                        } else Text("You do not have permission to deactivate these staff accounts.", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Ink)
                        if (plan.protectedStaff > 0) Text("${plan.protectedStaff} protected accounts stay: owners, platform admins, your own account, or managers of other branches. Their old branch assignment is cleared.", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Ink)
                        TextInput(confirm.confirmedName, { model.changeBranchRemoval(confirmedName = it) }, Modifier.fillMaxWidth(),
                            label = "Type the branch name to confirm", placeholder = confirm.branch.name)
                    }
                }
            }
        }
        is RestaurantConfirm.BranchStatus -> ConfirmDialog(
            title = when (confirm.status) { "ACTIVE" -> "Open ${confirm.branch.name} again?"; "TEMPORARILY_CLOSED" -> "Close ${confirm.branch.name} for now?"; else -> "Close ${confirm.branch.name}?" },
            message = if (confirm.status == "ACTIVE") "It's open in the POS, bookings and the kitchen again." else
                "It stays in the lists, marked closed. Its staff, devices and past sales stay.",
            confirmText = when (confirm.status) { "ACTIVE" -> "Open again"; "TEMPORARILY_CLOSED" -> "Close for now"; else -> "Close" },
            onConfirm = model::confirm, onDismiss = model::dismissConfirm, danger = confirm.status != "ACTIVE", busy = state.saving, extra = errorExtra
        )
        is RestaurantConfirm.RemoveAddress -> ConfirmDialog("Remove this address?", confirm.address.oneLine, "Remove", model::confirm,
            model::dismissConfirm, danger = true, busy = state.saving, extra = errorExtra)
        is RestaurantConfirm.RemoveContact -> ConfirmDialog("Remove ${confirm.contact.fullName}?", "They're no longer listed as a contact.", "Remove",
            model::confirm, model::dismissConfirm, danger = true, busy = state.saving, extra = errorExtra)
        is RestaurantConfirm.RemoveTax -> ConfirmDialog("Remove these tax details?", "Receipts stop printing these numbers.", "Remove",
            model::confirm, model::dismissConfirm, danger = true, busy = state.saving, extra = errorExtra)
    }
}

@Composable
private fun RemoveRestaurantDialog(state: RestaurantDetailsState, model: RestaurantDetailsScreenModel) {
    val name = state.restaurant?.name.orEmpty()
    var typed by remember { mutableStateOf("") }
    val matches = typed.trim().equals(name.trim(), ignoreCase = true)
    AppDialog("Remove $name?", model::dismissConfirm, busy = state.saving, maxWidth = 520.dp, buttons = {
        KitButton("Cancel", model::dismissConfirm, style = ButtonStyle.SECONDARY, enabled = !state.saving)
        KitButton("Remove restaurant", model::confirm, style = ButtonStyle.DANGER, loading = state.saving, enabled = matches)
    }) {
        state.error?.let { MessageBar(it, MessageKind.ERROR) }
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.WarningAmber, null, Modifier.size(22.dp), tint = Kit.Danger)
            Text("It disappears from the restaurant list and all its branches close. Its staff are signed out and can't sign in. " +
                "Sales, payments and records are kept.", fontFamily = Inter(), fontSize = 14.sp, lineHeight = 20.sp, color = Kit.Ink)
        }
        TextInput(typed, { typed = it }, Modifier.fillMaxWidth(), label = "Type the restaurant's name to confirm", placeholder = name)
    }
}

/** Where the owner's invitation stands: on its way, sent (and until when its link works), failed or expired. */
@Composable
private fun InviteNote(invite: com.saporini.mobile_desktop.platform.OwnerInviteStatusDto?) {
    val zone = kotlinx.datetime.TimeZone.currentSystemDefault()
    fun at(text: String?) = com.saporini.mobile_desktop.core.format.dateTimeText(text, zone)
    val (text, tone) = when {
        invite == null || invite.delivery == null ->
            "Not signed in yet. The invitation email has a link to set a password." to Kit.Amber
        invite.linkStatus == "EXPIRED" ->
            "The invitation link expired (${at(invite.linkExpiresAt)}) before they used it. Send a new one." to Kit.Danger
        invite.delivery == "FAILED" ->
            "The invitation couldn't be sent: ${invite.lastError ?: "the mail server refused it"}. Check the email address, then send it again." to Kit.Danger
        invite.delivery == "PENDING" && invite.lastError != null ->
            "The invitation is waiting: the mail server didn't take it yet (${invite.lastError}). Trying again at ${at(invite.nextAttemptAt)}." to Kit.Amber
        invite.delivery == "PENDING" || invite.delivery == "SENDING" ->
            "The invitation is on its way." to Kit.Amber
        else ->
            "Invitation sent ${at(invite.sentAt)}. Its link works until ${at(invite.linkExpiresAt)}; they haven't set a password yet." to Kit.Amber
    }
    NoteBox(Icons.Outlined.Mail, text, tone = tone)
}
