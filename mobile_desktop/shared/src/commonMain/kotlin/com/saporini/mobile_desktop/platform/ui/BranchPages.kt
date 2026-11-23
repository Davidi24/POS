package com.saporini.mobile_desktop.platform.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.FieldPair
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.OverviewTabs
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.i18n.Text
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.hours.OpenNowDto
import com.saporini.mobile_desktop.hours.OpeningHoursApi
import com.saporini.mobile_desktop.hours.OpeningHoursState
import com.saporini.mobile_desktop.hours.clockAt
import com.saporini.mobile_desktop.hours.hm
import com.saporini.mobile_desktop.hours.shortDayName
import com.saporini.mobile_desktop.hours.zoneOf
import com.saporini.mobile_desktop.platform.AddressDto
import com.saporini.mobile_desktop.platform.BranchDraft
import com.saporini.mobile_desktop.platform.parseCoordinates
import androidx.compose.material.icons.outlined.MyLocation
import com.saporini.mobile_desktop.platform.BranchDto
import com.saporini.mobile_desktop.platform.BranchView
import com.saporini.mobile_desktop.platform.MAX_BRANCH_NAME
import com.saporini.mobile_desktop.platform.MAX_CITY
import com.saporini.mobile_desktop.platform.MAX_CODE
import com.saporini.mobile_desktop.platform.MAX_DESCRIPTION
import com.saporini.mobile_desktop.platform.MAX_EMAIL_TEXT
import com.saporini.mobile_desktop.platform.MAX_POSTAL
import com.saporini.mobile_desktop.platform.MAX_STREET
import com.saporini.mobile_desktop.platform.RestaurantDetailsScreenModel
import com.saporini.mobile_desktop.platform.RestaurantDetailsState
import com.saporini.mobile_desktop.platform.branchProblems
import com.saporini.mobile_desktop.platform.branchStatusLabel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.todayIn
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.inventory_places
import mobile_desktop.shared.generated.resources.settings_reservations
import mobile_desktop.shared.generated.resources.settings_shifts
import mobile_desktop.shared.generated.resources.supplier_place
import org.koin.compose.koinInject
import kotlin.time.Clock

// Restaurant info → Branches and a branch's own page (user, 2026-11-22: "every branch can have them different, so you go
// inside a branch to do these"): the branch's name, phone, email, address and opening hours are changed there.

// The app's own green (user, 2026-11-23: the bright green for open didn't fit).
private val OpenGreen = Kit.Green
// The app's own red (user, 2026-11-23: "don't use colours never seen in the other pages").
private val ClosedRed = Kit.Danger

/** The branches as cards with their picture, where they are and whether they're open now; a card opens its page. */
@Composable
internal fun BranchesTab(state: RestaurantDetailsState, model: RestaurantDetailsScreenModel, size: ScreenSize, width: Dp) {
    val session = koinInject<SessionManager>()
    val acting by session.actingRestaurant.collectAsState()
    val now = rememberOpenNow(state)
    val zone = zoneOf(state.restaurant?.timezone)
    val restaurantKey = state.restaurantId ?: state.restaurant?.name.orEmpty()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionPicture(Res.drawable.supplier_place, null, if (size.isPhone) 46.dp else 54.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Branches", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Kit.Ink)
                if (state.branches.isNotEmpty()) {
                    val open = state.branches.count { it.open }
                    Text(if (open == state.branches.size) "${state.branches.size}" else "$open of ${state.branches.size} open",
                        Modifier.clip(RoundedCornerShape(50)).background(Kit.Tint).padding(horizontal = 10.dp, vertical = 3.dp),
                        fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Kit.Ink)
                }
            }
            Text(if (state.canEdit) "Each branch keeps its own address, phone, email and opening hours. Open one to see or change them."
                else "Open a branch to change its opening hours.", fontFamily = Inter(), fontSize = 12.5.sp, lineHeight = 17.sp, color = Kit.Muted)
        }
    }
    if (state.branches.isEmpty()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            OverviewEmpty("No branches yet", if (state.canManage) "Add the first branch so the POS, kitchen and bookings can be used."
                else "The Super Admin adds the restaurant's branches.", Icons.Outlined.Business,
                action = if (state.canManage) ({ KitButton("Add branch", model::newBranch) }) else null)
        }
        return
    }
    val columns = when {
        width >= 1080.dp -> 3
        width >= 640.dp -> 2
        else -> 1
    }
    state.branches.chunked(columns).forEach { row ->
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            row.forEach { branch ->
                BranchInfoCard(branch, state.branchAddresses[branch.id].orEmpty().firstOrNull(), now[branch.id], zone,
                    here = acting?.branchId == branch.id, restaurantKey = restaurantKey, hoursOnly = !state.canEdit,
                    modifier = Modifier.weight(1f).fillMaxHeight()) { model.openBranch(branch.id) }
            }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Whether each branch is open right now, from its opening hours (loaded once per list of branches). */
@Composable
private fun rememberOpenNow(state: RestaurantDetailsState): Map<String, OpenNowDto> {
    val api = remember { org.koin.mp.KoinPlatform.getKoinOrNull()?.getOrNull<OpeningHoursApi>() }
    val restaurantId = state.restaurantId
    val ids = state.branches.map { it.id }
    val loaded by produceState(emptyMap<String, OpenNowDto>(), restaurantId, ids, api) {
        if (api == null || restaurantId == null) return@produceState
        value = coroutineScope {
            ids.map { id -> async { id to runCatching { api.now(restaurantId, id) }.getOrNull() } }.awaitAll()
        }.mapNotNull { (id, now) -> now?.let { id to it } }.toMap()
    }
    return loaded
}

/** "Open now · until 23:30", "Closed now · Opens tomorrow at 11:00", "Opening hours not set", and its colour. */
internal fun nowLine(now: OpenNowDto?, zone: TimeZone): Pair<String, Color>? {
    now ?: return null
    return when {
        !now.set -> "Opening hours not set" to Kit.Amber
        now.why == "OPENED" -> "Open outside the hours · until ${clockAt(now.openedUntil ?: now.until, zone)?.hm().orEmpty()}" to Kit.Amber
        now.open -> "Open now · until ${clockAt(now.until, zone)?.hm().orEmpty()}" to OpenGreen
        else -> listOfNotNull("Closed now", com.saporini.mobile_desktop.hours.ui.nextOpenText(now.nextOpen, zone)).joinToString(" · ") to ClosedRed
    }
}

/** One branch: its picture with whether it's open, its name and code, where it is, how guests reach it, open now. */
@Composable
private fun BranchInfoCard(
    branch: BranchDto,
    address: AddressDto?,
    now: OpenNowDto?,
    zone: TimeZone,
    here: Boolean,
    restaurantKey: String,
    hoursOnly: Boolean,
    modifier: Modifier,
    onOpen: () -> Unit
) {
    val accent = accentOf(restaurantKey)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val lift by animateDpAsState(if (hovered) 12.dp else 2.dp, label = "branch-card-lift")
    val shape = RoundedCornerShape(18.dp)
    Surface(
        onClick = onOpen, interactionSource = interaction,
        modifier = modifier.shadow(lift, shape, ambientColor = Color(0x16000000), spotColor = Color(0x1C000000)),
        shape = shape, color = Color.White, border = BorderStroke(if (hovered) 1.5.dp else 1.dp, if (hovered) accent.strong.copy(alpha = 0.55f) else Kit.Border)
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(132.dp)) {
                CoverBackground(branch.coverUrl, accent)
                DotPill(branchStatusLabel(branch.status, branch.isActive), if (branch.open) OpenGreen else Kit.Grey,
                    Modifier.align(Alignment.TopStart).padding(10.dp), onWhite = true)
                if (here) DotPill("You're here", Kit.Blue, Modifier.align(Alignment.TopEnd).padding(10.dp), onWhite = true)
                Box(
                    Modifier.align(Alignment.BottomStart).padding(12.dp).size(38.dp).shadow(4.dp, CircleShape).clip(CircleShape).background(Color.White),
                    Alignment.Center
                ) { Icon(Icons.Filled.Place, null, Modifier.size(21.dp), tint = accent.strong) }
            }
            Box(Modifier.fillMaxWidth().height(3.dp).background(accent.strong))
            Column(Modifier.fillMaxWidth().weight(1f).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("BRANCH", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp, color = accent.strong)
                    if (branch.code.isNotBlank()) CodeTag(branch.code, accent)
                }
                Text(branch.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Kit.Ink, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, translate = false)
                IconLine(Icons.Outlined.Place, address?.oneLine?.ifBlank { null } ?: "No address yet", missing = address == null, maxLines = 2)
                IconLine(Icons.Outlined.Phone, branch.phone ?: "The restaurant's phone", missing = branch.phone == null)
                IconLine(Icons.Outlined.Mail, branch.email ?: "The restaurant's email", missing = branch.email == null)
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val line = nowLine(now, zone)
                    if (line != null) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(line.second))
                            Text(line.first, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = line.second, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                    } else Spacer(Modifier.weight(1f))
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(if (hovered) accent.strong else accent.softer).padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(if (hoursOnly) "Opening hours" else "Manage", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.5.sp,
                            color = if (hovered) Color.White else accent.strong, maxLines = 1)
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(15.dp), tint = if (hovered) Color.White else accent.strong)
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeTag(code: String, accent: Accent) {
    Text(code, Modifier.clip(RoundedCornerShape(5.dp)).background(accent.softer).border(1.dp, accent.soft, RoundedCornerShape(5.dp))
        .padding(horizontal = 6.dp, vertical = 1.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp,
        color = accent.strong, maxLines = 1, translate = false)
}

// ---- A branch's own page ----

/**
 * The top of a branch's page: the plain back arrow to the branches, a map pin, "BRANCH" and its code, its name, the path
 * back (Branches › name) and whether it's open now, on its picture fading into white.
 */
@Composable
internal fun BranchBand(branch: BranchDto, now: OpenNowDto?, state: RestaurantDetailsState, model: RestaurantDetailsScreenModel, narrow: Boolean) {
    val accent = accentOf(state.restaurantId ?: state.restaurant?.name.orEmpty())
    val zone = zoneOf(state.restaurant?.timezone)
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border), shadowElevation = 1.dp) {
        Box(Modifier.fillMaxWidth().height(if (narrow) 124.dp else 150.dp)) {
            if (!narrow) {
                Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.5f)) {
                    CoverBackground(branch.coverUrl, accent)
                    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.White, Color.White.copy(alpha = 0.5f), Color.Transparent))))
                }
            } else {
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.White, accent.softer, accent.soft))))
            }
            if (state.superAdmin) {
                CoverButton(branch.coverUrl != null, branch.id in state.coverBusy, { model.uploadCover(branch.id, it) }, { model.removeCover(branch.id) },
                    model::coverProblem, Modifier.align(Alignment.TopEnd).padding(12.dp))
            }
            Row(Modifier.fillMaxSize().padding(start = 10.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (narrow) 10.dp else 14.dp)) {
                PlainBackArrow("Back to the branches", model::leaveBranch)
                Box(Modifier.size(if (narrow) 50.dp else 64.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(Color.White), Alignment.Center) {
                    Icon(Icons.Filled.Place, null, Modifier.size(if (narrow) 26.dp else 32.dp), tint = accent.strong)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("BRANCH", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.5.sp, letterSpacing = 0.8.sp, color = accent.strong)
                        if (branch.code.isNotBlank()) CodeTag(branch.code, accent)
                    }
                    Text(branch.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (narrow) 20.sp else 25.sp, color = Kit.Ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, translate = false)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Branches", Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = model::leaveBranch), fontFamily = Inter(),
                            fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Green)
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(14.dp), tint = Kit.Faint)
                        Text(branch.name, fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, translate = false)
                    }
                    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DotPill(branchStatusLabel(branch.status, branch.isActive), if (branch.open) OpenGreen else Kit.Grey)
                        if (!narrow) nowLine(now, zone)?.let { (text, color) ->
                            Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = color, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** Under the band: Details | Opening hours, and (Super Admin) closing or opening the branch. */
@Composable
internal fun BranchSwitchRow(branch: BranchDto, state: RestaurantDetailsState, model: RestaurantDetailsScreenModel, narrow: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.canEdit) {
            OverviewTabs(BranchView.entries.map { it.label }, state.branchView.label,
                { label -> BranchView.entries.firstOrNull { it.label == label }?.let(model::branchView) },
                if (narrow) Modifier.weight(1f) else Modifier.width(320.dp), height = 40.dp)
        } else {
            Text("Opening hours", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Kit.Ink)
        }
        if (!narrow) Spacer(Modifier.weight(1f))
        if (state.canManage && !narrow) {
            if (branch.open) {
                KitButton("Close for now", { model.askBranchStatus(branch, "TEMPORARILY_CLOSED") }, icon = Icons.Outlined.Pause,
                    style = ButtonStyle.SECONDARY, enabled = !state.saving)
                KitButton("Close", { model.askBranchStatus(branch, "INACTIVE") }, icon = Icons.Outlined.Block, style = ButtonStyle.SECONDARY,
                    enabled = !state.saving)
            } else {
                KitButton("Open again", { model.askBranchStatus(branch, "ACTIVE") }, icon = Icons.Outlined.PlayArrow, enabled = !state.saving)
            }
        }
    }
}

/** Whether the branch form differs from the saved branch. */
internal fun branchChanged(state: RestaurantDetailsState, branch: BranchDto? = state.openBranch): Boolean {
    val draft = state.branch ?: return false
    branch ?: return false
    if (draft.id != branch.id) return false
    return draft != com.saporini.mobile_desktop.platform.branchDraftOf(branch, state.branchAddresses[branch.id].orEmpty().firstOrNull())
        .copy(showProblems = draft.showProblems)
}

/**
 * A branch's details, changed in place and saved with the floating bar: the branch (name and code: the Super Admin's;
 * a few words), how guests reach it, where it is. Beside them: how guests see it and its week at a glance.
 */
@Composable
internal fun BranchDetails(
    branch: BranchDto,
    state: RestaurantDetailsState,
    model: RestaurantDetailsScreenModel,
    size: ScreenSize,
    wide: Boolean,
    hours: OpeningHoursState?
) {
    val form: @Composable (Modifier) -> Unit = { m -> Column(m, verticalArrangement = Arrangement.spacedBy(16.dp)) { BranchForm(branch, state, model, size) } }
    val side: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BranchPreview(branch, state, size)
            HoursGlance(state, model, hours, size)
        }
    }
    if (wide) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            form(Modifier.weight(1.5f))
            side(Modifier.weight(1f))
        }
    } else {
        form(Modifier.fillMaxWidth())
        side(Modifier.fillMaxWidth())
    }
    if (state.canManage) {
        if (size.isPhone) InfoSection("Open or closed", "Closing keeps its staff, devices and past sales.", icon = Icons.Outlined.Storefront, compact = true) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (branch.open) {
                    KitButton("Close for now", { model.askBranchStatus(branch, "TEMPORARILY_CLOSED") }, style = ButtonStyle.SECONDARY, enabled = !state.saving)
                    KitButton("Close", { model.askBranchStatus(branch, "INACTIVE") }, style = ButtonStyle.SECONDARY, enabled = !state.saving)
                } else KitButton("Open again", { model.askBranchStatus(branch, "ACTIVE") }, enabled = !state.saving)
            }
        }
        RemoveZone("Remove the branch", "Only when nothing uses it any more: no open orders, bookings to come, people clocked in, staff or devices. " +
            "Its sales are kept.", "Remove branch", compact = size.isPhone, enabled = !state.saving) { model.askRemoveBranch(branch) }
    }
}

@Composable
internal fun BranchForm(branch: BranchDto, state: RestaurantDetailsState, model: RestaurantDetailsScreenModel, size: ScreenSize) {
    val draft = state.branch?.takeIf { it.id == branch.id }
    val address = state.branchAddresses[branch.id].orEmpty().firstOrNull()
    val problems = if (draft?.showProblems == true) branchProblems(draft) else emptyMap()
    val editable = draft != null && !state.saving
    val locked = !state.superAdmin
    val compact = size.isPhone
    val change = { f: (BranchDraft) -> BranchDraft -> model.changeBranch(f) }
    InfoSection("The branch", "Its name on receipts and the booking page, and a few words about it.", picture = Res.drawable.supplier_place,
        compact = compact, trailing = if (locked) ({ LockedNote("Name and code: set by the Super Admin") }) else null) {
        FieldPair(
            { m -> TextInput(draft?.name ?: branch.name, { v -> change { it.copy(name = v) } }, m, label = "Name", required = !locked,
                icon = Icons.Outlined.Storefront, problem = problems["name"], maxLength = MAX_BRANCH_NAME, enabled = editable && !locked) },
            { m -> TextInput(draft?.code ?: branch.code, { v -> change { it.copy(code = v) } }, m, label = "Code", icon = Icons.Outlined.Badge,
                problem = problems["code"], maxLength = MAX_CODE, enabled = editable && !locked, hint = "A short name the system uses, e.g. MAIN") },
            wide = !compact
        )
        TextInput(draft?.description ?: branch.description.orEmpty(), { v -> change { it.copy(description = v) } }, Modifier.fillMaxWidth(),
            label = "Description", optional = true, singleLine = false, minLines = 3, icon = Icons.Outlined.Description, problem = problems["description"],
            maxLength = MAX_DESCRIPTION, enabled = editable, placeholder = "By the sea, with a terrace for the summer.")
    }
    InfoSection("How guests reach this branch", "The same as the restaurant's contact: a change here changes it in Restaurant info → Details too.",
        picture = Res.drawable.settings_reservations, compact = compact) {
        FieldPair(
            { m -> TextInput(draft?.phone ?: branch.phone.orEmpty(), { v -> change { it.copy(phone = v) } }, m, label = "Phone", optional = true,
                icon = Icons.Outlined.Phone, problem = problems["phone"], enabled = editable, placeholder = state.restaurant?.phone ?: "+355 69 123 4567") },
            { m -> TextInput(draft?.email ?: branch.email.orEmpty(), { v -> change { it.copy(email = v.trim()) } }, m, label = "Email", optional = true,
                icon = Icons.Outlined.Mail, problem = problems["email"], maxLength = MAX_EMAIL_TEXT, enabled = editable,
                placeholder = state.restaurant?.email ?: "branch@restaurant.al") },
            wide = !compact
        )
    }
    InfoSection("Where it is", "The address guests find it at, printed on its receipts.", picture = Res.drawable.inventory_places, compact = compact) {
        TextInput(draft?.street ?: address?.streetLine1.orEmpty(), { v -> change { it.copy(street = v) } }, Modifier.fillMaxWidth(), label = "Street",
            icon = Icons.Outlined.Place, problem = problems["street"], maxLength = MAX_STREET, enabled = editable, placeholder = "Rruga e Durrësit 12")
        val postal: @Composable (Modifier) -> Unit = { m ->
            TextInput(draft?.postalCode ?: address?.postalCode.orEmpty(), { v -> change { it.copy(postalCode = v) } }, m, label = "Postal code",
                problem = problems["postalCode"], maxLength = MAX_POSTAL, enabled = editable)
        }
        val city: @Composable (Modifier) -> Unit = { m ->
            TextInput(draft?.city ?: address?.city.orEmpty(), { v -> change { it.copy(city = v) } }, m, label = "City", problem = problems["city"],
                maxLength = MAX_CITY, enabled = editable)
        }
        val country: @Composable (Modifier) -> Unit = { m ->
            TextInput(draft?.country ?: address?.country.orEmpty(), { v -> change { it.copy(country = v) } }, m, label = "Country",
                problem = problems["country"], maxLength = MAX_CITY, enabled = editable)
        }
        if (compact) {
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
        ExactLocationField(draft?.coordinates ?: address?.let { a -> a.latitude?.let { lat -> a.longitude?.let { lng ->
            com.saporini.mobile_desktop.platform.coordinatesText(lat to lng) } } }.orEmpty(), problems["coordinates"], editable) { v -> change { it.copy(coordinates = v) } }
    }
}

/**
 * The branch's exact spot, if wanted (user, 2026-11-23: "I can put the exact coordinates if I want"): latitude and longitude
 * in one field, pasted from a map, with a link to check them on the map once they're right.
 */
@Composable
internal fun ExactLocationField(value: String, problem: String?, enabled: Boolean, onChange: (String) -> Unit) {
    val point = parseCoordinates(value)
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextInput(value, onChange, Modifier.fillMaxWidth(), label = "Exact location", optional = true, icon = Icons.Outlined.MyLocation,
            problem = problem, maxLength = 60, enabled = enabled, placeholder = "41.32750, 19.81870",
            hint = "Latitude, longitude. In Google Maps, right-click the place and click the numbers to copy them.")
        if (point != null) LinkText("Check it on the map ›") {
            runCatching { uri.openUri("https://www.google.com/maps?q=${point.first},${point.second}") }
        }
    }
}

/** A lock and a short line: what only the Super Admin changes. */
@Composable
internal fun LockedNote(text: String) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(Kit.Tint).padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(Icons.Outlined.Lock, null, Modifier.size(13.dp), tint = Kit.Muted)
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
    }
}

/** What guests see of this branch, drawn from the form as it's typed. */
@Composable
internal fun BranchPreview(branch: BranchDto, state: RestaurantDetailsState, size: ScreenSize) {
    val restaurant = state.restaurant ?: return
    val draft = state.branch?.takeIf { it.id == branch.id }
    val address = state.branchAddresses[branch.id].orEmpty().firstOrNull()
    val name = (draft?.name ?: branch.name).ifBlank { "Your branch" }
    val street = draft?.let { listOfNotNull(it.street.ifBlank { null }, listOf(it.postalCode, it.city).filter { p -> p.isNotBlank() }.joinToString(" ").ifBlank { null })
        .joinToString(", ") } ?: address?.oneLine.orEmpty()
    val phone = (draft?.phone ?: branch.phone.orEmpty()).ifBlank { restaurant.phone.orEmpty() }
    val email = (draft?.email ?: branch.email.orEmpty()).ifBlank { restaurant.email.orEmpty() }
    val accent = accentOf(state.restaurantId ?: restaurant.name)
    InfoSection("How guests see it", "The top of its booking page, as you type.", icon = Icons.Outlined.Language, compact = size.isPhone) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, Kit.Border, RoundedCornerShape(14.dp))) {
            Box(Modifier.fillMaxWidth().height(86.dp)) { CoverBackground(branch.coverUrl ?: restaurant.coverUrl, accent) }
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.offsetUpBy(22.dp).shadow(4.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(3.dp)) {
                    RestaurantLogo(state.branding?.logoUrl, restaurant.name, 46.dp)
                }
                Text(restaurant.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = Kit.Muted, translate = false)
                Text(name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Kit.Ink, maxLines = 2, translate = false)
                IconLine(Icons.Outlined.Place, street.ifBlank { "Add the address so guests find it" }, missing = street.isBlank(), maxLines = 2)
                IconLine(Icons.Outlined.Phone, phone.ifBlank { "No phone yet" }, missing = phone.isBlank())
                IconLine(Icons.Outlined.Mail, email.ifBlank { "No email yet" }, missing = email.isBlank())
                Box(Modifier.padding(top = 4.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Green).padding(vertical = 10.dp), Alignment.Center) {
                    Text("Book a table", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                }
            }
        }
    }
}

/** The branch's week at a glance (today in bold) and a button to its opening hours. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HoursGlance(state: RestaurantDetailsState, model: RestaurantDetailsScreenModel, hours: OpeningHoursState?, size: ScreenSize) {
    val loaded = hours?.hours
    val zone = zoneOf(loaded?.timezone ?: state.restaurant?.timezone)
    val line = nowLine(loaded?.now, zone)
    InfoSection("Opening hours", line?.first ?: "Its week and special days.", picture = Res.drawable.settings_shifts, compact = size.isPhone,
        trailing = { KitButton(if (loaded?.set == false) "Set them" else "Change", { model.branchView(BranchView.HOURS) }, icon = Icons.Outlined.Schedule,
            style = ButtonStyle.SECONDARY) }) {
        when {
            loaded == null -> Text("Loading…", fontFamily = Inter(), fontSize = 12.5.sp, color = Kit.Faint)
            !loaded.set -> Text("Not set yet: until they are, the branch counts as always open.", fontFamily = Inter(), fontSize = 12.5.sp,
                lineHeight = 17.sp, color = Kit.Amber)
            else -> {
                val today = Clock.System.todayIn(zone).dayOfWeek.isoDayNumber
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Canvas).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    (1..7).forEach { day ->
                        val spans = loaded.week.firstOrNull { it.day == day }?.spans.orEmpty()
                        val on = day == today
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(shortDayName(day), Modifier.width(52.dp), fontFamily = Inter(), fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                                fontSize = 12.5.sp, color = if (on) Kit.Green else Kit.Ink)
                            Text(if (spans.isEmpty()) "Closed" else spans.joinToString(" · ") { "${it.opens}–${it.closes}" }, Modifier.weight(1f),
                                fontFamily = Inter(), fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, fontSize = 12.5.sp,
                                color = if (spans.isEmpty()) ClosedRed else Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (on) Text("Today", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = Kit.Green)
                        }
                    }
                }
                val ahead = loaded.specialDays.count { (it.endsOn ?: it.startsOn) >= Clock.System.todayIn(zone).toString() }
                if (ahead > 0) Text(if (ahead == 1) "1 special day ahead" else "$ahead special days ahead", fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted)
            }
        }
    }
}

/** Removing something for good, last on its page and on its own, in red; on a phone the button goes under the words. */
@Composable
internal fun RemoveZone(title: String, text: String, button: String, compact: Boolean, enabled: Boolean, onRemove: () -> Unit) {
    val about: @Composable (Modifier) -> Unit = { m ->
        Row(m, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Kit.Danger.copy(alpha = 0.1f)), Alignment.Center) {
                Icon(Icons.Outlined.Delete, null, Modifier.size(20.dp), tint = Kit.Danger)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink)
                Text(text, fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp, color = Kit.Muted)
            }
        }
    }
    val box = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Kit.Danger.copy(alpha = 0.035f))
        .border(1.dp, Kit.Danger.copy(alpha = 0.22f), RoundedCornerShape(18.dp)).padding(if (compact) 14.dp else 18.dp)
    if (compact) {
        Column(box, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            about(Modifier.fillMaxWidth())
            KitButton(button, onRemove, Modifier.fillMaxWidth(), icon = Icons.Outlined.Delete, style = ButtonStyle.DANGER_OUTLINE, enabled = enabled)
        }
    } else {
        Row(box, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            about(Modifier.weight(1f))
            KitButton(button, onRemove, icon = Icons.Outlined.Delete, style = ButtonStyle.DANGER_OUTLINE, enabled = enabled)
        }
    }
}

/** Moves something up by [by] (a logo sitting half on a picture). */
internal fun Modifier.offsetUpBy(by: Dp): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = by.roundToPx()
    layout(placeable.width, (placeable.height - shift).coerceAtLeast(0)) { placeable.place(0, -shift) }
}
