package com.saporini.mobile_desktop.platform.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Text
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
internal fun RestaurantInfoScreen(modifier: Modifier = Modifier) {
    val model = koinInject<RestaurantDetailsScreenModel>()
    BindToLifecycle(model::setActive, model::onDispose)
    val state by model.state.collectAsState()
    RestaurantDetailsContent(state, model, state.tab, model::tab, showTabs = true, modifier = modifier)
}

@Composable
internal fun RestaurantDetailsContent(
    state: RestaurantDetailsState,
    model: RestaurantDetailsScreenModel,
    tab: RestaurantTab,
    onTab: (RestaurantTab) -> Unit,
    showTabs: Boolean,
    modifier: Modifier = Modifier
) {
    val tabs = if (state.superAdmin) RestaurantTab.entries else RestaurantTab.entries - RestaurantTab.MONEY
    val shown = if (tab in tabs) tab else RestaurantTab.DETAILS
    // The Details tab is the form itself: it holds the saved details, ready to change in place (again after a save or Discard).
    LaunchedEffect(shown, state.details == null, state.restaurant?.version, state.canEdit) {
        if (shown == RestaurantTab.DETAILS && state.details == null && state.canEdit && state.restaurant != null) model.openDetailsForm()
    }
    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        val size = screenSizeFor(maxWidth)
        Column(
            Modifier.fillMaxSize().padding(horizontal = if (size.isPhone) 14.dp else 22.dp, vertical = if (size.isPhone) 12.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val restaurant = state.restaurant
            RestaurantHero(state, size) {
                RefreshButton(state.loading, model::refresh, size = if (size.isPhone) 38.dp else com.saporini.mobile_desktop.core.components.PageControlHeight)
                TabActions(shown, state, model, compact = size.isPhone)
            }
            if (showTabs) RestaurantTabs(tabs, shown, onTab)
            ScreenMessages(state.error.takeIf { !anyDialogOpen(state.copy(details = null)) && !(shown == RestaurantTab.DETAILS && detailsChanged(state)) },
                state.notice, model::clearMessages)
            when {
                !state.canRead -> PageState(PageStateKind.EMPTY, "Access unavailable", "You do not have permission to view restaurant details.")
                state.restaurantId == null -> PageState(PageStateKind.EMPTY, "No restaurant", "Open a restaurant first.")
                !state.loaded && state.loading -> OverviewPageSkeleton(size)
                !state.loaded && state.error != null -> PageState(PageStateKind.FAILED, action = { RetryText(onClick = model::refresh) })
                restaurant == null -> PageState(PageStateKind.LOADING)
                else -> Box(Modifier.fillMaxWidth().weight(1f)) {
                    val unsaved = shown == RestaurantTab.DETAILS && detailsChanged(state)
                    com.saporini.mobile_desktop.core.components.ScrollColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        when (shown) {
                            RestaurantTab.DETAILS -> DetailsTab(state, model, size)
                            RestaurantTab.BRANCHES -> BranchesTab(state, model, size)
                            RestaurantTab.PLACES -> PlacesTab(state, model, size)
                            RestaurantTab.BRANDING -> BrandingTab(state, model, size)
                            RestaurantTab.LEGAL -> LegalTab(state, model)
                            RestaurantTab.MONEY -> MoneyTab(state, model, size)
                        }
                        Spacer(Modifier.height(if (unsaved) 96.dp else 12.dp))
                    }
                    if (unsaved) com.saporini.mobile_desktop.core.components.UnsavedChangesBar(
                        saving = state.saving,
                        problem = state.details?.takeIf { it.showProblems }?.let { d -> detailsProblems(d).values.firstOrNull() },
                        error = state.error,
                        onDiscard = model::cancelDetails, onSave = model::saveDetails,
                        message = if (size.isPhone) "Unsaved changes" else "You changed the restaurant's details",
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)
                    )
                }
            }
        }
    }
    Dialogs(state, model)
}

private fun anyDialogOpen(state: RestaurantDetailsState) = state.details != null || state.branch != null || state.address != null ||
    state.contact != null || state.tax != null || state.currency != null || state.timezone != null || state.confirm != null ||
    state.ownerStep != null || state.transfer != null

@Composable
private fun TabActions(tab: RestaurantTab, state: RestaurantDetailsState, model: RestaurantDetailsScreenModel, compact: Boolean) {
    if (!state.canRead || !state.canEdit) return
    // On a phone the buttons share the row with the logo, so they're squares with just an icon that says what they add.
    val action: @Composable (String, ImageVector, ImageVector, Boolean, () -> Unit) -> Unit = { text, icon, phoneIcon, primary, onClick ->
        when {
            compact -> SquareAction(phoneIcon, text, primary, !state.saving, onClick)
            primary -> ToolbarPrimaryButton(text, icon, !state.saving, onClick)
            else -> ToolbarButton(text, icon, !state.saving, onClick)
        }
    }
    when (tab) {
        // Details is edited right on the page (the form below), so there's no Edit button.
        RestaurantTab.DETAILS -> {}
        RestaurantTab.BRANCHES -> if (state.canManage) action("Add branch", Icons.Outlined.Add, Icons.Outlined.AddBusiness, true, model::newBranch)
        RestaurantTab.PLACES -> {
            action("Add contact", Icons.Outlined.Person, Icons.Outlined.PersonAdd, false, model::newContact)
            action("Add address", Icons.Outlined.Add, Icons.Outlined.AddLocationAlt, true, model::newAddress)
        }
        RestaurantTab.LEGAL -> if (state.canManage) action("Add tax details", Icons.Outlined.Add, Icons.Outlined.PostAdd, true, model::newTax)
        else -> {}
    }
}

/** A phone's 38 dp square button with just an icon: green for the tab's main action, white otherwise. */
@Composable
private fun SquareAction(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, primary: Boolean, enabled: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.Surface(onClick = onClick, enabled = enabled, modifier = Modifier.size(38.dp), shape = RoundedCornerShape(8.dp),
        color = if (primary) Kit.Green.copy(alpha = if (enabled) 1f else 0.45f) else Color.White,
        border = if (primary) null else androidx.compose.foundation.BorderStroke(1.dp, Kit.Border)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, Modifier.size(19.dp), tint = if (primary) Color.White else Kit.Ink) }
    }
}

// ---- The top of the page ----

/**
 * The restaurant at the top of the page, like a profile: a band in its own colour (Logo & receipts → main colour) with
 * its logo on it, the name and whether it's open, then what matters at a glance (code, branches, currency, time zone)
 * as small chips. [actions] (refresh, the tab's button) sit on the right.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RestaurantHero(state: RestaurantDetailsState, size: ScreenSize, actions: @Composable () -> Unit) {
    val restaurant = state.restaurant
    val name = restaurant?.name ?: "Restaurant"
    val brand = parseHexColor(state.branding?.primaryColor)
    val accent = brand?.let { Accent(it, it.copy(alpha = 0.26f), it.copy(alpha = 0.07f)) } ?: accentOf(name)
    val logo = if (size.isPhone) 64.dp else 84.dp
    val band = if (size.isPhone) 84.dp else 120.dp
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // The logo sits half on the band; the page's buttons sit beside it, just under the band (on every size, so the
        // name below keeps the whole width).
        Box(Modifier.fillMaxWidth().height(band + if (size.isPhone) 44.dp else logo / 2)) {
            Box(Modifier.fillMaxWidth().height(band).clip(RoundedCornerShape(16.dp))) { CoverBackground(null, accent) }
            Box(Modifier.align(Alignment.BottomStart).padding(start = if (size.isPhone) 14.dp else 18.dp).clip(RoundedCornerShape(logo * 0.3f))
                .background(Color.White).padding(4.dp)) {
                RestaurantLogo(state.branding?.logoUrl, name, logo)
            }
            Row(Modifier.align(Alignment.BottomEnd), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (size.isPhone) 8.dp else 10.dp)) { actions() }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(name, Modifier.weight(1f, fill = false), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (size.isPhone) 20.sp else 24.sp,
                lineHeight = if (size.isPhone) 25.sp else 30.sp, color = Kit.Ink, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            restaurant?.let { StatusChip(restaurantStatusLabel(it.status, it.isActive), if (it.open) Kit.Green else Kit.Amber, strong = true) }
        }
        restaurant?.let {
            val openBranches = state.branches.count { b -> b.open }
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FactChip(Icons.Outlined.Badge, it.code)
                FactChip(Icons.Outlined.Business, when {
                    state.branches.isEmpty() -> "No branches yet"
                    openBranches == state.branches.size -> "${state.branches.size} ${if (state.branches.size == 1) "branch" else "branches"}"
                    else -> "${state.branches.size} branches · $openBranches open"
                })
                FactChip(Icons.Outlined.CurrencyExchange, "${it.currency} · ${currencyName(it.currency)}")
                FactChip(Icons.Outlined.Schedule, timeZoneLabel(it.timezone))
            }
        }
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

/** "#4F7942" → the colour, or null when it isn't one. */
private fun parseHexColor(text: String?): Color? {
    val hex = text?.trim()?.removePrefix("#")?.takeIf { it.length == 6 && it.all { c -> c.isDigit() || c.lowercaseChar() in 'a'..'f' } } ?: return null
    return Color(0xFF000000 or hex.toLong(16))
}

/** The page's parts as underlined tabs with icons (the chosen one green); they slide sideways on a phone. */
@Composable
private fun RestaurantTabs(tabs: List<RestaurantTab>, chosen: RestaurantTab, onTab: (RestaurantTab) -> Unit) {
    val scroll = androidx.compose.foundation.rememberScrollState()
    val spans = remember { androidx.compose.runtime.mutableStateMapOf<RestaurantTab, Pair<Int, Int>>() }
    com.saporini.mobile_desktop.core.components.CenterChosen(scroll, chosen, spans)
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(Kit.Border))
        Row(Modifier.fillMaxWidth().fadingEdges(scroll).horizontalScroll(scroll)) {
            tabs.forEach { tab ->
                val on = tab == chosen
                Column(Modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Max)
                    .onGloballyPositioned { spans[tab] = it.positionInParent().x.roundToInt() to it.size.width }
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).clickable { onTab(tab) }.padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(tabIcon(tab), null, Modifier.size(17.dp), tint = if (on) Kit.Green else Kit.Muted)
                        Text(tab.label, fontFamily = Inter(), fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, fontSize = 13.5.sp,
                            letterSpacing = 0.sp, color = if (on) Kit.Green else Kit.Ink, maxLines = 1, softWrap = false)
                    }
                    Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(if (on) Kit.Green else Color.Transparent))
                }
            }
        }
    }
}

private fun tabIcon(tab: RestaurantTab) = when (tab) {
    RestaurantTab.DETAILS -> Icons.Outlined.Storefront
    RestaurantTab.BRANCHES -> Icons.Outlined.Business
    RestaurantTab.PLACES -> Icons.Outlined.Place
    RestaurantTab.BRANDING -> Icons.Outlined.Image
    RestaurantTab.LEGAL -> Icons.Outlined.Gavel
    RestaurantTab.MONEY -> Icons.Outlined.CurrencyExchange
}

/** A white card with an icon and a title, for one topic of the details. */
@Composable
private fun InfoCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.material3.Surface(modifier, shape = RoundedCornerShape(14.dp), color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Kit.Border)) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 14.dp)) {
            Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, Modifier.size(18.dp), tint = Kit.Ink)
                Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink)
            }
            content()
        }
    }
}

/** One fact in an info card: a small icon tile, what it is, and its value ("Not set" in grey when there's none). */
@Composable
private fun InfoLine(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String?, last: Boolean = false) {
    val shown = value?.takeIf { it.isNotBlank() }
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Kit.Tint), Alignment.Center) {
            Icon(icon, null, Modifier.size(17.dp), tint = Kit.Muted)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = Kit.Muted)
            Text(shown ?: "Not set", fontFamily = Inter(), fontWeight = if (shown != null) FontWeight.SemiBold else FontWeight.Normal, fontSize = 14.sp,
                color = if (shown != null) Kit.Ink else Kit.Faint, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
    if (!last) Box(Modifier.padding(start = 46.dp).fillMaxWidth().height(1.dp).background(Kit.RowBorder))
}

// ---- Details ----

@Composable
private fun DetailsTab(state: RestaurantDetailsState, model: RestaurantDetailsScreenModel, size: ScreenSize) {
    val restaurant = state.restaurant ?: return
    // The form beside a live preview of what guests see (under it on narrow screens, the booking page and the email
    // line side by side on a tablet).
    if (size.isDesktop) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1.55f), verticalArrangement = Arrangement.spacedBy(14.dp)) { DetailsForm(state, model, size) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) { GuestPreview(state, wide = false, compact = false) }
        }
    } else {
        DetailsForm(state, model, size)
        GuestPreview(state, wide = !size.isPhone, compact = size.isPhone)
    }
    if (state.superAdmin) {
        PanelPair(size, { m -> OwnerPanel(state, model, m) }, { m -> OpenClosePanel(state, model, m) })
    }
    // Removing the restaurant comes last, on its own (user rule).
    if (state.canDelete) DangerZone(state, model, compact = size.isPhone)
}

/** Whether the details form differs from what's saved. */
private fun detailsChanged(state: RestaurantDetailsState): Boolean {
    val d = state.details ?: return false
    val r = state.restaurant ?: return false
    return d.name != r.name || d.legalName != r.legalName || d.code != r.code || d.description != r.description.orEmpty() ||
        d.email != r.email.orEmpty() || d.phone != r.phone.orEmpty() || d.website != r.website.orEmpty()
}

/**
 * The restaurant's details as a form in three cards: who it is (name, legal name, code: only the Super Admin changes
 * them, so the owner sees them locked), about it (the description guests read) and how guests reach it (email, phone,
 * website). Changes are saved with the floating bar; problems show under each field once Save was pressed.
 */
@Composable
private fun DetailsForm(state: RestaurantDetailsState, model: RestaurantDetailsScreenModel, size: ScreenSize) {
    val restaurant = state.restaurant ?: return
    val draft = state.details
    val problems = if (draft?.showProblems == true) detailsProblems(draft) else emptyMap()
    val editable = draft != null && !state.saving
    val locked = !state.superAdmin
    val value = { saved: String?, drafted: String? -> drafted ?: saved.orEmpty() }
    // Two fields a row, one under the other on a phone.
    val pairs = !size.isPhone
    val compact = size.isPhone
    FormCard(Icons.Outlined.Storefront, "Who the restaurant is", "Its name on receipts, the booking website and every email.", compact,
        trailing = if (locked) ({ LockedPill("Set by the Super Admin") }) else null) {
        com.saporini.mobile_desktop.core.components.FieldPair(
            { m -> TextInput(value(restaurant.name, draft?.name), { v -> model.changeDetails { it.copy(name = v) } }, m, label = "Name", required = !locked,
                icon = Icons.Outlined.Storefront, problem = problems["name"], maxLength = MAX_RESTAURANT_NAME, enabled = editable && !locked) },
            { m -> TextInput(value(restaurant.legalName, draft?.legalName), { v -> model.changeDetails { it.copy(legalName = v) } }, m, label = "Legal name",
                required = !locked, icon = Icons.Outlined.Gavel, problem = problems["legalName"], maxLength = MAX_LEGAL_NAME, enabled = editable && !locked,
                hint = "As registered, e.g. on invoices") },
            wide = pairs
        )
        TextInput(value(restaurant.code, draft?.code), { v -> model.changeDetails { it.copy(code = v) } }, Modifier.fillMaxWidth(), label = "Code",
            required = !locked, icon = Icons.Outlined.Badge, problem = problems["code"], maxLength = MAX_CODE, enabled = editable && !locked,
            hint = "A short name the system uses, e.g. SAPORINI_TIRANA")
    }
    FormCard(Icons.Outlined.Description, "About the restaurant", "A few words guests read on the booking website.", compact) {
        TextInput(value(restaurant.description, draft?.description), { v -> model.changeDetails { it.copy(description = v) } }, Modifier.fillMaxWidth(),
            label = "Description", optional = true, singleLine = false, minLines = 4, problem = problems["description"], maxLength = MAX_DESCRIPTION,
            enabled = editable, placeholder = "A family trattoria in the heart of Tirana: fresh pasta every day and a garden for the summer.")
    }
    FormCard(Icons.Outlined.Phone, "How guests reach you", "Shown on the booking website and in every booking email; receipts print the phone. " +
        "A branch's own phone and email come first.", compact) {
        com.saporini.mobile_desktop.core.components.FieldPair(
            { m -> TextInput(value(restaurant.email, draft?.email), { v -> model.changeDetails { it.copy(email = v.trim()) } }, m, label = "Email", required = true,
                icon = Icons.Outlined.Mail, problem = problems["email"], maxLength = MAX_EMAIL_TEXT, enabled = editable, placeholder = "hello@restaurant.al") },
            { m -> TextInput(value(restaurant.phone, draft?.phone), { v -> model.changeDetails { it.copy(phone = v) } }, m, label = "Phone", required = true,
                icon = Icons.Outlined.Phone, problem = problems["phone"], enabled = editable, placeholder = "+355 69 123 4567") },
            wide = pairs
        )
        TextInput(value(restaurant.website, draft?.website), { v -> model.changeDetails { it.copy(website = v.trim()) } }, Modifier.fillMaxWidth(),
            label = "Website", optional = true, icon = Icons.Outlined.Language, problem = problems["website"], maxLength = MAX_WEBSITE, enabled = editable,
            placeholder = "https://")
    }
}

/**
 * A form card: an icon tile, a title and a short line on what it's for, an optional pill on the right (under the line
 * on a phone, [compact]), then the fields.
 */
@Composable
private fun FormCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, note: String, compact: Boolean,
                     trailing: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.material3.Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Kit.Border)) {
        Column(Modifier.padding(if (compact) 14.dp else 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(if (compact) 36.dp else 40.dp).clip(RoundedCornerShape(11.dp)).background(Kit.GreenSoft), Alignment.Center) {
                    Icon(icon, null, Modifier.size(if (compact) 18.dp else 20.dp), tint = Kit.Green)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink)
                    Text(note, fontFamily = Inter(), fontSize = 12.sp, lineHeight = 16.sp, color = Kit.Muted)
                    if (compact) trailing?.let { Box(Modifier.padding(top = 4.dp)) { it() } }
                }
                if (!compact) trailing?.invoke()
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Kit.RowBorder))
            content()
        }
    }
}

@Composable
private fun LockedPill(text: String) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(Kit.Tint).padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(Icons.Outlined.Lock, null, Modifier.size(13.dp), tint = Kit.Muted)
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
    }
}

/**
 * What guests see, drawn from the form as it's typed: the top of the booking website (logo, name, description) and the
 * contact line their booking emails end with. [wide] (a tablet, where the preview has the page's whole width) puts the
 * two side by side.
 */
@Composable
private fun GuestPreview(state: RestaurantDetailsState, wide: Boolean, compact: Boolean) {
    val restaurant = state.restaurant ?: return
    val d = state.details
    val name = (d?.name ?: restaurant.name).ifBlank { "Your restaurant" }
    val description = (d?.description ?: restaurant.description.orEmpty()).trim()
    val phone = (d?.phone ?: restaurant.phone.orEmpty()).trim()
    val email = (d?.email ?: restaurant.email.orEmpty()).trim()
    val website = (d?.website ?: restaurant.website.orEmpty()).trim()
    // A small booking page.
    val page: @Composable (Modifier) -> Unit = { m ->
        Column(m.clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Kit.Border, RoundedCornerShape(12.dp))) {
            Box(Modifier.fillMaxWidth().height(56.dp).background(Kit.GreenSoft))
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.offsetUp(22.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(3.dp)) {
                    RestaurantLogo(state.branding?.logoUrl, name, 48.dp)
                }
                Text(name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Kit.Ink, maxLines = 2)
                Text(description.ifBlank { "Add a description so guests know what to expect." }, fontFamily = Inter(), fontSize = 12.5.sp, lineHeight = 18.sp,
                    color = if (description.isBlank()) Kit.Faint else Kit.Muted, maxLines = 4, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Green).padding(vertical = 10.dp), Alignment.Center) {
                    Text("Book a table", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                }
            }
        }
    }
    // The line booking emails end with.
    val emailEnd: @Composable (Modifier) -> Unit = { m ->
        Row(m.clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Kit.Border, RoundedCornerShape(12.dp))
            .padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Mail, null, Modifier.size(17.dp), tint = Kit.Muted)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("End of a booking email", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Muted)
                Text(listOfNotNull(phone.ifBlank { null }?.let { "Call $it" }, email.ifBlank { null }?.let { "write to $it" })
                    .joinToString(" · ").ifBlank { "Add a phone and an email" }.let { "Questions? $it" },
                    fontFamily = Inter(), fontSize = 12.5.sp, lineHeight = 17.sp, color = Kit.Ink)
                if (website.isNotBlank()) Text(website, fontFamily = Inter(), fontSize = 12.sp, color = Kit.Green, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
    }
    androidx.compose.material3.Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Kit.Canvas,
        border = androidx.compose.foundation.BorderStroke(1.dp, Kit.Border)) {
        Column(Modifier.padding(if (compact) 14.dp else 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Language, null, Modifier.size(18.dp), tint = Kit.Ink)
                Text("How guests see it", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink)
            }
            Text("The booking website and booking emails, as you type.", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
            if (wide) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    page(Modifier.weight(1f))
                    emailEnd(Modifier.weight(1f))
                }
            } else {
                page(Modifier.fillMaxWidth())
                emailEnd(Modifier.fillMaxWidth())
            }
        }
    }
}

/** Moves something up by [by] (the logo sits half on the preview's band). */
private fun Modifier.offsetUp(by: androidx.compose.ui.unit.Dp): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = by.roundToPx()
    layout(placeable.width, (placeable.height - shift).coerceAtLeast(0)) { placeable.place(0, -shift) }
}

/** Removing the restaurant, last on the page and on its own, in red; on a phone ([compact]) the button goes under the words. */
@Composable
private fun DangerZone(state: RestaurantDetailsState, model: RestaurantDetailsScreenModel, compact: Boolean) {
    val about: @Composable (Modifier) -> Unit = { m ->
        Row(m, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(Kit.Danger.copy(alpha = 0.1f)), Alignment.Center) {
                Icon(Icons.Outlined.Delete, null, Modifier.size(20.dp), tint = Kit.Danger)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Remove the restaurant", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink)
                Text("It disappears everywhere and its staff are signed out. Its sales and records are kept.", fontFamily = Inter(), fontSize = 12.sp,
                    lineHeight = 16.sp, color = Kit.Muted)
            }
        }
    }
    val box = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Kit.Danger.copy(alpha = 0.04f))
        .border(1.dp, Kit.Danger.copy(alpha = 0.25f), RoundedCornerShape(14.dp)).padding(if (compact) 14.dp else 18.dp)
    if (compact) {
        Column(box, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            about(Modifier.fillMaxWidth())
            KitButton("Remove restaurant", model::askRemoveRestaurant, Modifier.fillMaxWidth(), icon = Icons.Outlined.Delete, style = ButtonStyle.DANGER_OUTLINE,
                enabled = !state.saving)
        }
    } else {
        Row(box, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            about(Modifier.weight(1f))
            KitButton("Remove restaurant", model::askRemoveRestaurant, icon = Icons.Outlined.Delete, style = ButtonStyle.DANGER_OUTLINE, enabled = !state.saving)
        }
    }
}

@Composable
private fun OwnerPanel(state: RestaurantDetailsState, model: RestaurantDetailsScreenModel, modifier: Modifier) {
    InfoCard(Icons.Outlined.Person, "Owner", modifier) {
        val owner = state.owner
        if (owner == null) {
            Text(if (state.restaurant?.ownerUserId == null) "No owner yet. Add the person who owns the restaurant; they get an email to set a password."
                else "The owner's account couldn't be found.", fontFamily = Inter(), fontSize = 13.sp, lineHeight = 18.sp, color = Kit.Muted)
            if (state.restaurant?.ownerUserId == null && state.canManage) {
                KitButton("Add the owner", model::startAddOwner, icon = Icons.Outlined.Person, enabled = !state.saving)
            }
        } else {
            LabeledValue("Name", owner.displayName)
            LabeledValue("Email", owner.email)
            if (!owner.emailVerified) {
                InviteNote(state.invite)
                if (state.canManage) KitButton("Send the invitation again", model::resendOwnerInvite, icon = Icons.AutoMirrored.Outlined.Send, style = ButtonStyle.SECONDARY,
                    enabled = !state.saving)
            } else {
                StatusChip("Signed up", Kit.Green, icon = Icons.Outlined.CheckCircle)
            }
            if (state.canTransferOwner) {
                KitButton("Transfer ownership", model::startTransfer, icon = Icons.Outlined.SwapHoriz, style = ButtonStyle.SECONDARY,
                    enabled = !state.saving)
            }
        }
        OwnerHistory(state.ownerTransfers)
    }
}

@Composable
private fun OpenClosePanel(state: RestaurantDetailsState, model: RestaurantDetailsScreenModel, modifier: Modifier) {
    val restaurant = state.restaurant ?: return
    InfoCard(Icons.Outlined.Block, "Open or closed", modifier) {
        Text(
            if (restaurant.open) "Open: its staff sign in and work as usual."
            else "${restaurantStatusLabel(restaurant.status, restaurant.isActive)}: its staff can't sign in. Data stays.",
            fontFamily = Inter(), fontSize = 13.sp, lineHeight = 18.sp, color = Kit.Ink
        )
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

// ---- Branches ----

@Composable
private fun BranchesTab(state: RestaurantDetailsState, model: RestaurantDetailsScreenModel, size: ScreenSize) {
    val session = koinInject<SessionManager>()
    val acting by session.actingRestaurant.collectAsState()
    if (state.branches.isEmpty()) {
        OverviewEmpty("No branches yet", "Add the first branch so the POS, kitchen and bookings can be used.", Icons.Outlined.Business,
            action = if (state.canManage) ({ KitButton("Add branch", model::newBranch, icon = Icons.Outlined.Add) }) else null)
        return
    }
    ListPanel(Modifier.fillMaxWidth()) {
        state.branches.forEach { branch ->
            val address = state.branchAddresses[branch.id].orEmpty().firstOrNull()
            val actions = buildList {
                if (state.canEdit) add(RowAction("Edit", Icons.Outlined.Edit) { model.editBranch(branch) })
                if (state.canManage) {
                    if (branch.open) {
                        add(RowAction("Close for now", Icons.Outlined.Pause) { model.askBranchStatus(branch, "TEMPORARILY_CLOSED") })
                        add(RowAction("Close", Icons.Outlined.Block) { model.askBranchStatus(branch, "INACTIVE") })
                    } else {
                        add(RowAction("Open again", Icons.Outlined.PlayArrow) { model.askBranchStatus(branch, "ACTIVE") })
                    }
                    add(RowAction("Remove", Icons.Outlined.Delete, danger = true) { model.askRemoveBranch(branch) })
                }
            }
            TableRow(onClick = if (state.canEdit) ({ model.editBranch(branch) }) else null) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(if (branch.open) Kit.GreenSoft else Kit.Canvas), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Business, null, Modifier.size(20.dp), tint = if (branch.open) Kit.Green else Kit.Grey)
                }
                if (size.isPhone) {
                    // A phone: the name and where it is, then how to reach it and whether it's open, one under the other.
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        CellText(branch.name, listOfNotNull(branch.code, address?.oneLine).joinToString(" · "))
                        listOfNotNull(branch.phone, branch.email).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                            Text(it, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusChip(branchStatusLabel(branch.status, branch.isActive), if (branch.open) Kit.Green else Kit.Grey)
                            if (acting?.branchId == branch.id) StatusChip("You're here", Kit.Blue)
                        }
                    }
                } else {
                    CellText(branch.name, listOfNotNull(branch.code, address?.oneLine).joinToString(" · "), Modifier.weight(1.6f))
                    CellText(branch.phone ?: "No phone", branch.email, Modifier.weight(1f), strong = false)
                    Row(Modifier.weight(0.9f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusChip(branchStatusLabel(branch.status, branch.isActive), if (branch.open) Kit.Green else Kit.Grey)
                        if (acting?.branchId == branch.id) StatusChip("You're here", Kit.Blue)
                    }
                }
                RowActionsMenu(actions, state.saving)
            }
        }
    }
    NoteBox(Icons.Outlined.Info, if (state.superAdmin)
        "A branch that's still in use (open orders, bookings to come, people clocked in, staff or devices) can't be removed. Close it instead, or move those first."
    else "The owner keeps each branch's phone, email and address up to date. Adding, closing or removing branches is for the Super Admin.")
}

// ---- Address & contacts ----

@Composable
private fun PlacesTab(state: RestaurantDetailsState, model: RestaurantDetailsScreenModel, size: ScreenSize) {
    PanelPair(size, { m ->
        OverviewPanel(Icons.Outlined.Place, "Addresses", m, count = state.addresses.size) {
            if (state.addresses.isEmpty()) {
                Text("No address yet. Receipts print the address guests find the restaurant at.", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted)
            }
            state.addresses.forEach { address ->
                ItemRow(
                    title = address.oneLine.ifBlank { "Address" },
                    detail = addressTypeLabel(address.addressType),
                    chip = if (address.isPrimary) "Main" else null,
                    actions = if (state.canEdit) listOf(
                        RowAction("Edit", Icons.Outlined.Edit) { model.editAddress(address) },
                        RowAction("Remove", Icons.Outlined.Delete, danger = true) { model.askRemoveAddress(address) }
                    ) else emptyList(),
                    busy = state.saving,
                    onClick = if (state.canEdit) ({ model.editAddress(address) }) else null
                )
            }
        }
    }, { m ->
        OverviewPanel(Icons.Outlined.Person, "Contacts", m, count = state.contacts.size) {
            if (state.contacts.isEmpty()) {
                Text("No contact people yet (accounting, a manager, support).", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted)
            }
            state.contacts.forEach { contact ->
                ItemRow(
                    title = contact.fullName,
                    detail = listOfNotNull(contactTypeLabel(contact.contactType), contact.jobTitle, contact.email, contact.phone).joinToString(" · "),
                    chip = if (contact.isPrimary) "Main" else null,
                    actions = if (state.canEdit) listOf(
                        RowAction("Edit", Icons.Outlined.Edit) { model.editContact(contact) },
                        RowAction("Remove", Icons.Outlined.Delete, danger = true) { model.askRemoveContact(contact) }
                    ) else emptyList(),
                    busy = state.saving,
                    onClick = if (state.canEdit) ({ model.editContact(contact) }) else null
                )
            }
        }
    })
}

@Composable
private fun ItemRow(title: String, detail: String?, chip: String?, actions: List<RowAction>, busy: Boolean, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, Kit.RowBorder, RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CellText(title, detail, Modifier.weight(1f))
        chip?.let { StatusChip(it, Kit.Green) }
        RowActionsMenu(actions, busy)
    }
}

// ---- Logo & receipts ----

@Composable
private fun BrandingTab(state: RestaurantDetailsState, model: RestaurantDetailsScreenModel, size: ScreenSize) {
    val scope = rememberCoroutineScope()
    val picker = rememberFilePickerLauncher(type = FileKitType.Image) { file ->
        if (file != null) scope.launch {
            val contentType = photoContentType(file.name)
            val bytes = runCatching { file.readBytes() }.getOrNull()
            when {
                contentType == null -> model.logoProblem("Use a PNG, JPEG or WebP picture for the logo.")
                bytes == null -> model.logoProblem("This picture couldn't be read. Try another one.")
                bytes.size > MaxPhotoBytes -> model.logoProblem("The logo is too big. Use a picture under 5 MB.")
                runCatching { bytes.decodeToImageBitmap() }.isFailure -> model.logoProblem("This file can't be opened as a picture.")
                else -> model.uploadLogo(bytes, file.name, contentType)
            }
        }
    }
    val draft = state.brandingDraft
    PanelPair(size, { m ->
        OverviewPanel(Icons.Outlined.Image, "Logo", m) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(120.dp).clip(RoundedCornerShape(16.dp)).background(Kit.Canvas).border(1.dp, Kit.Border, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center) {
                    if (state.uploadingLogo) CircularProgressIndicator(Modifier.size(28.dp), color = Kit.Green)
                    else RemoteImage(state.branding?.logoUrl, "Logo", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) {
                        Icon(Icons.Outlined.Image, null, Modifier.size(36.dp).align(Alignment.Center), tint = Kit.Faint)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("PNG, JPEG or WebP, up to 5 MB. A square picture looks best.", fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp, color = Kit.Muted)
                    if (state.canEdit) {
                        KitButton(if (state.branding?.logoUrl == null) "Upload logo" else "Replace logo", { picker.launch() }, icon = Icons.Outlined.Upload,
                            enabled = !state.uploadingLogo && !state.saving)
                        if (state.branding?.logoUrl != null) KitButton("Remove logo", model::removeLogo, icon = Icons.Outlined.Delete,
                            style = ButtonStyle.SECONDARY, enabled = !state.uploadingLogo && !state.saving)
                    }
                }
            }
            GroupLabel("Colours")
            val main: @Composable (Modifier) -> Unit = { m ->
                TextInput(draft.primaryColor, { v -> model.changeBranding { it.copy(primaryColor = v) } }, m, label = "Main colour",
                    placeholder = "#4F7942", enabled = state.canEdit, problem = com.saporini.mobile_desktop.platform.colorProblem(draft.primaryColor))
            }
            val second: @Composable (Modifier) -> Unit = { m ->
                TextInput(draft.secondaryColor, { v -> model.changeBranding { it.copy(secondaryColor = v) } }, m, label = "Second colour",
                    placeholder = "#EEF3EB", enabled = state.canEdit, problem = com.saporini.mobile_desktop.platform.colorProblem(draft.secondaryColor))
            }
            if (size.isPhone) {
                // One colour a row on a phone, each with its swatch.
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                    ColorSwatch(draft.primaryColor, 46.dp)
                    main(Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                    ColorSwatch(draft.secondaryColor, 46.dp)
                    second(Modifier.weight(1f))
                }
            } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                ColorSwatch(draft.primaryColor, 46.dp)
                main(Modifier.weight(1f))
                ColorSwatch(draft.secondaryColor, 46.dp)
                second(Modifier.weight(1f))
            }
        }
    }, { m ->
        OverviewPanel(Icons.Outlined.Receipt, "Receipt texts", m) {
            TextInput(draft.receiptHeader, { v -> model.changeBranding { it.copy(receiptHeader = v) } }, Modifier.fillMaxWidth(), label = "Top of the receipt",
                placeholder = "e.g. Thank you for dining with us", singleLine = false, minLines = 2, maxLength = MAX_RECEIPT_TEXT, enabled = state.canEdit)
            TextInput(draft.receiptFooter, { v -> model.changeBranding { it.copy(receiptFooter = v) } }, Modifier.fillMaxWidth(), label = "Bottom of the receipt",
                placeholder = "e.g. Wi-Fi: Saporini · see you soon", singleLine = false, minLines = 2, maxLength = MAX_RECEIPT_TEXT, enabled = state.canEdit)
            if (state.canEdit) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KitButton("Save", model::saveBranding, loading = state.saving, enabled = state.brandingChanged)
                if (state.brandingChanged) KitButton("Undo", model::resetBranding, style = ButtonStyle.SECONDARY, enabled = !state.saving)
            }
        }
    })
    ReceiptPreview(state)
}

/** A small receipt drawn from the saved details, so the effect of each field is easy to see. */
@Composable
private fun ReceiptPreview(state: RestaurantDetailsState) {
    val restaurant = state.restaurant ?: return
    val address = state.addresses.firstOrNull { it.isPrimary } ?: state.addresses.firstOrNull()
    val tax = state.taxProfiles.firstOrNull { it.isDefault && it.effectiveAt(kotlin.time.Clock.System.now()) }
    OverviewPanel(Icons.Outlined.Description, "How a receipt starts", Modifier.fillMaxWidth()) {
        Column(
            // A till receipt's width, narrower on a small phone.
            Modifier.widthIn(max = 320.dp).fillMaxWidth().align(Alignment.CenterHorizontally).clip(RoundedCornerShape(6.dp)).background(Color(0xFFFFFEFB))
                .border(1.dp, Kit.Border, RoundedCornerShape(6.dp)).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            RemoteImage(state.branding?.logoUrl, null, Modifier.size(48.dp), contentScale = ContentScale.Fit) {}
            ReceiptLine(restaurant.name, bold = true)
            if (restaurant.legalName.isNotBlank() && restaurant.legalName != restaurant.name) ReceiptLine(restaurant.legalName)
            address?.let { ReceiptLine(it.oneLine) } ?: ReceiptLine("(no address yet)", faint = true)
            (state.branches.firstOrNull()?.phone ?: restaurant.phone)?.let { ReceiptLine("Tel. $it") }
            tax?.vatNumber?.let { ReceiptLine("VAT $it") } ?: ReceiptLine("(no VAT number yet)", faint = true)
            state.brandingDraft.receiptHeader.takeIf { it.isNotBlank() }?.let { ReceiptLine(it) }
            ReceiptLine("- - - - - - - - - - - - - - -", faint = true)
            ReceiptLine("1 × Espresso ........ ${restaurant.currency} 1.50", faint = true)
            ReceiptLine("- - - - - - - - - - - - - - -", faint = true)
            state.brandingDraft.receiptFooter.takeIf { it.isNotBlank() }?.let { ReceiptLine(it) }
        }
    }
}

@Composable
private fun ReceiptLine(text: String, bold: Boolean = false, faint: Boolean = false) {
    Text(text, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontSize = if (bold) 13.sp else 11.sp, lineHeight = 15.sp, color = if (faint) Kit.Faint else Kit.Ink, textAlign = TextAlign.Center)
}

// ---- Legal & tax ----

@Composable
private fun LegalTab(state: RestaurantDetailsState, model: RestaurantDetailsScreenModel) {
    val restaurant = state.restaurant ?: return
    OverviewPanel(Icons.Outlined.Gavel, "Legal identity", Modifier.fillMaxWidth()) {
        LabeledValue("Legal name", restaurant.legalName.ifBlank { "–" })
        LabeledValue("Registered address", state.addresses.firstOrNull { it.addressType == "LEGAL" }?.oneLine ?: "Same as the main address")
    }
    OverviewPanel(Icons.Outlined.Badge, "Tax and VAT", Modifier.fillMaxWidth(), count = state.taxProfiles.size) {
        if (state.taxProfiles.isEmpty()) {
            Text("No tax details yet. Receipts print the VAT and tax numbers of the main tax details.", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted)
        }
        state.taxProfiles.forEach { tax ->
            ItemRow(
                title = listOfNotNull(tax.vatNumber?.let { "VAT $it" }, tax.taxNumber?.let { "Tax no. $it" }).joinToString(" · ").ifBlank { tax.country },
                detail = listOfNotNull(tax.country, tax.fiscalCode?.let { "Fiscal code $it" }, tax.taxOffice).joinToString(" · "),
                chip = if (tax.isDefault) "On receipts" else null,
                actions = if (state.canManage) listOf(
                    RowAction("Edit", Icons.Outlined.Edit) { model.editTax(tax) },
                    RowAction("Remove", Icons.Outlined.Delete, danger = true) { model.askRemoveTax(tax) }
                ) else emptyList(),
                busy = state.saving,
                onClick = if (state.canManage) ({ model.editTax(tax) }) else null
            )
        }
        if (!state.superAdmin) NoteBox(Icons.Outlined.Lock, "Tax and VAT details are the restaurant's legal identity. Ask the Super Admin to change them.")
    }
}

// ---- Currency & time zone ----

@Composable
private fun MoneyTab(state: RestaurantDetailsState, model: RestaurantDetailsScreenModel, size: ScreenSize) {
    val restaurant = state.restaurant ?: return
    PanelPair(size, { m ->
        OverviewPanel(Icons.Outlined.CurrencyExchange, "Currency", m) {
            Text("${restaurant.currency} · ${currencyName(restaurant.currency)}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Kit.Ink)
            Text("Changing it converts every price, wage, cost and fixed fee at the rate you enter. You see every amount first and can correct any of them. " +
                "Past sales keep their own currency; Statistics shows each currency separately.",
                fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp, color = Kit.Muted)
            if (state.canManage) KitButton("Change currency", model::startCurrencyChange, icon = Icons.Outlined.CurrencyExchange, enabled = !state.saving)
            if (state.currencyChanges.isNotEmpty()) {
                GroupLabel("Earlier changes")
                state.currencyChanges.forEach { change ->
                    ItemRow("${change.oldCurrency} → ${change.newCurrency} · 1 ${change.oldCurrency} = ${change.rate.value} ${change.newCurrency}",
                        listOfNotNull(dateTimeText(change.changedAt, TimeZone.of(restaurant.timezone)), change.changedByName,
                            "${change.valuesConverted} amounts").joinToString(" · "), null, emptyList(), false, null)
                }
            }
        }
    }, { m ->
        OverviewPanel(Icons.Outlined.Schedule, "Time zone", m) {
            Text(timeZoneLabel(restaurant.timezone), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Kit.Ink)
            Text(restaurant.timezone, fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
            Text("Opening hours, menu hours, bookings and reports follow it. Before changing it you see what moves.",
                fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp, color = Kit.Muted)
            if (state.canManage) KitButton("Change time zone", model::startTimezoneChange, icon = Icons.Outlined.Language, enabled = !state.saving)
        }
    })
}

@Composable
private fun PanelPair(size: ScreenSize, first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit) {
    // Equal heights side by side on wide screens (panel content avoids lazy lists and BoxWithConstraints).
    com.saporini.mobile_desktop.core.components.PanelRow(size, 1f to first, 1f to second)
}

// ---- Dialogs ----

@Composable
private fun Dialogs(state: RestaurantDetailsState, model: RestaurantDetailsScreenModel) {
    state.branch?.let { BranchDialog(it, state, model) }
    state.address?.let { AddressDialog(it, state, model) }
    state.contact?.let { ContactDialog(it, state, model) }
    state.tax?.let { TaxDialog(it, state, model) }
    state.currency?.let { CurrencyDialog(it, state, model) }
    state.timezone?.let { TimezoneDialog(it, state, model) }
    state.confirm?.let { ConfirmDialogs(it, state, model) }
    state.ownerStep?.let { OwnerDialog(it, model::editOwner, model::saveOwner, model::cancelOwner) }
    state.transfer?.let { OwnerTransferDialog(it, model) }
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
private fun CurrencyDialog(flow: CurrencyFlow, state: RestaurantDetailsState, model: RestaurantDetailsScreenModel) {
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
                problem = if (flow.rateText.isNotBlank() && flow.rate == null) "A number above 0, up to 8 decimals" else null)
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
