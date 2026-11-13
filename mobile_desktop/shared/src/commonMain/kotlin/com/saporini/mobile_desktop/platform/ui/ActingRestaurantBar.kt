package com.saporini.mobile_desktop.platform.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.session.ActingRestaurant
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.platform.BranchDto
import com.saporini.mobile_desktop.platform.PlatformRepository
import com.saporini.mobile_desktop.platform.branchStatusLabel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * For super admins on the workspace picker: the restaurant and branch they're working in, switching branch, and the
 * way back to every restaurant.
 */
@Composable
internal fun ActingRestaurantBar(acting: ActingRestaurant, onAllRestaurants: () -> Unit) {
    val session = koinInject<SessionManager>()
    val repository = koinInject<PlatformRepository>()
    val model = remember(repository, session) { com.saporini.mobile_desktop.platform.BranchListScreenModel(repository, session) }
    com.saporini.mobile_desktop.core.components.BindToLifecycle(model::setActive, model::onDispose)
    androidx.compose.runtime.LaunchedEffect(acting.restaurantId) { model.show(acting.restaurantId, acting.restaurantName) }
    val state by model.state.collectAsState()
    var open by remember { mutableStateOf(false) }

    Surface(shape = RoundedCornerShape(50), color = Color.White, border = BorderStroke(1.dp, Kit.Border), shadowElevation = 2.dp) {
        Row(Modifier.height(44.dp).padding(start = 6.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(50)).background(Kit.GreenSoft), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Storefront, null, Modifier.size(18.dp), tint = Kit.Green)
            }
            Text(acting.restaurantName, Modifier.padding(start = 10.dp).widthIn(max = 260.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold,
                fontSize = 14.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box {
                Row(
                    Modifier.padding(start = 6.dp).clip(RoundedCornerShape(50))
                        .clickable {
                            open = true
                            model.refresh()
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Outlined.Business, null, Modifier.size(15.dp), tint = Kit.Muted)
                    Text(acting.branchName ?: "Manage restaurant", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Outlined.ExpandMore, "Switch branch", Modifier.size(16.dp), tint = Kit.Muted)
                }
                DropdownMenu(open, { open = false }, Modifier.background(Color.White)) {
                    when {
                        state.loading || state.entering != null -> DropdownMenuItem(text = { CircularProgressIndicator(Modifier.size(18.dp), color = Kit.Green, strokeWidth = 2.dp) }, onClick = {})
                        state.error != null -> DropdownMenuItem(text = { Text("Branches unavailable · Try again", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Danger) }, onClick = model::refresh)
                        !state.restaurantOpen -> DropdownMenuItem(text = { Text("This restaurant is closed", fontFamily = Inter(), fontSize = 13.sp) }, enabled = false, onClick = {})
                        state.cards.isEmpty() -> DropdownMenuItem(text = { Text("No branches yet", fontFamily = Inter(), fontSize = 13.sp) }, enabled = false, onClick = {})
                        else -> {
                            state.cards.forEach { branch ->
                                DropdownMenuItem(
                                    leadingIcon = { if (branch.id == acting.branchId) Icon(Icons.Outlined.Check, null, Modifier.size(16.dp), tint = Kit.Green) else Box(Modifier.width(16.dp)) },
                                    text = {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(branch.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                                color = if (branch.id == acting.branchId) Kit.Green else Kit.Ink)
                                            if (!branch.open) Text(branchStatusLabel(branch.status, branch.active), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Faint)
                                        }
                                    },
                                    enabled = branch.open,
                                    onClick = { model.choose(branch) { open = false } }
                                )
                            }
                            // The branches come a page at a time; the rest load here instead of all at once.
                            if (state.loadingMore) {
                                DropdownMenuItem(text = { CircularProgressIndicator(Modifier.size(18.dp), color = Kit.Green, strokeWidth = 2.dp) }, onClick = {})
                            } else if (state.hasNext) {
                                DropdownMenuItem(
                                    text = { Text("Show more branches (${state.total - state.cards.size} more)", fontFamily = Inter(),
                                        fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Green) },
                                    onClick = model::loadMore
                                )
                            }
                        }
                    }
                }
            }
            Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(22.dp).background(Kit.Border))
            Row(
                Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onAllRestaurants).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(Icons.Outlined.SwapHoriz, null, Modifier.size(16.dp), tint = Kit.Green)
                Text("All restaurants", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Green)
            }
        }
    }
}
