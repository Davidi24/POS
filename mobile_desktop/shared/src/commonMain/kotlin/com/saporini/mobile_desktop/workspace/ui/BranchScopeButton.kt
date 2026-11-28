package com.saporini.mobile_desktop.workspace.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.i18n.Text
import com.saporini.mobile_desktop.core.session.BranchScope
import com.saporini.mobile_desktop.core.session.ScopeBranch
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.session.Workspace
import com.saporini.mobile_desktop.core.session.branchLevel
import com.saporini.mobile_desktop.core.session.canSeeEveryBranch
import com.saporini.mobile_desktop.core.session.globalLevel
import com.saporini.mobile_desktop.core.theme.Inter
import org.koin.compose.koinInject

/**
 * Where a workspace is looking (user, 2026-11-23, step 26): the branch the person is in, another branch, or all of them
 * where the workspace has a level for the whole restaurant. In every workspace's bar, for those who may see more than their
 * own branch, once the restaurant has two or more.
 */
@Composable
internal fun BranchScopeButton(workspace: Workspace, compact: Boolean) {
    val session = koinInject<SessionManager>()
    val user by session.currentUser.collectAsState()
    val repository = koinInject<com.saporini.mobile_desktop.platform.PlatformRepository>()
    val restaurantId = user?.restaurantId
    val allowed = canSeeEveryBranch(user)
    LaunchedEffect(restaurantId, allowed) {
        if (restaurantId != null && allowed && (BranchScope.restaurantId != restaurantId || BranchScope.branches.isEmpty())) {
            runCatching { repository.branches(restaurantId) }.getOrNull()?.let { list ->
                BranchScope.loaded(restaurantId, list.sortedWith(compareByDescending<com.saporini.mobile_desktop.platform.BranchDto> { it.open }
                    .thenBy { it.name.lowercase() }).map { ScopeBranch(it.id, it.name, it.code, it.open) })
            }
        }
    }
    if (!allowed || restaurantId == null || BranchScope.restaurantId != restaurantId || BranchScope.branches.size < 2) return
    val all = BranchScope.isAll(workspace)
    val here = BranchScope.branch(user?.defaultBranchId)
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(onClick = { open = true }, shape = RoundedCornerShape(50), color = if (all) Kit.GreenSoft else Color.White,
            border = BorderStroke(1.dp, if (all) Kit.Green.copy(alpha = 0.45f) else Kit.Border)) {
            Row(Modifier.height(if (compact) 34.dp else 40.dp).padding(start = 10.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(if (all) Icons.Outlined.Public else Icons.Outlined.Place, null, Modifier.size(if (compact) 16.dp else 18.dp), tint = Kit.Green)
                Text(if (all) "All branches" else here?.name ?: "Choose a branch", Modifier.widthIn(max = if (compact) 110.dp else 170.dp),
                    fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = if (compact) 12.5.sp else 13.5.sp, color = Kit.Ink, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, translate = all)
                Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.size(18.dp), tint = Kit.Muted)
            }
        }
        DropdownMenu(open, { open = false }, Modifier.background(Color.White).width(320.dp)) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("WHERE YOU'RE LOOKING", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.5.sp, letterSpacing = 0.8.sp, color = Kit.Muted)
                Text(when {
                    workspace == Workspace.POS -> "Its orders and payments, branch by branch. Menus, prices and settings are set once for every branch."
                    workspace == Workspace.KDS -> "Each branch has its own kitchen: pick the branch to see it."
                    else -> "One branch, or all of them together."
                }, fontFamily = Inter(), fontSize = 12.sp, lineHeight = 16.sp, color = Kit.Muted)
            }
            workspace.globalLevel?.let { global ->
                // Every branch together comes after the first launch: shown, marked "Coming soon".
                val soon = !com.saporini.mobile_desktop.core.session.ALL_BRANCHES_ON
                ScopeLine(Icons.Outlined.Public, "All branches", global, chosen = all, enabled = !soon, tint = if (soon) Kit.Grey else Kit.Green,
                    tag = if (soon) "Coming soon" else null) {
                    BranchScope.showAll(workspace, true); open = false
                }
                Box(Modifier.padding(horizontal = 14.dp, vertical = 4.dp).fillMaxWidth().height(1.dp).background(Kit.RowBorder))
            }
            BranchScope.branches.forEach { branch ->
                val detail = when {
                    !branch.open -> "Closed"
                    branch.id == session.homeBranchId -> "Your branch · ${workspace.branchLevel}"
                    else -> workspace.branchLevel
                }
                ScopeLine(Icons.Outlined.Place, branch.name, detail, chosen = !all && branch.id == user?.defaultBranchId, enabled = branch.open,
                    tint = if (branch.open) Kit.Blue else Kit.Grey, translateTitle = false) {
                    BranchScope.showAll(workspace, false)
                    if (branch.id != user?.defaultBranchId) session.workIn(branch.id, branch.name)
                    open = false
                }
            }
        }
    }
}

@Composable
private fun ScopeLine(icon: ImageVector, title: String, detail: String, chosen: Boolean, enabled: Boolean, tint: Color,
                      translateTitle: Boolean = true, tag: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp).clip(RoundedCornerShape(10.dp))
        .background(if (chosen) Kit.GreenSoft else Color.Transparent).then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(tint.copy(alpha = if (enabled) 0.12f else 0.08f)), Alignment.Center) {
            Icon(icon, null, Modifier.size(17.dp), tint = tint)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.5.sp, letterSpacing = 0.sp,
                color = if (enabled) Kit.Ink else Kit.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis, translate = translateTitle)
            Text(detail, fontFamily = Inter(), fontSize = 11.5.sp, letterSpacing = 0.sp, color = Kit.Muted, maxLines = 2)
        }
        tag?.let {
            Text(it, Modifier.clip(RoundedCornerShape(50)).background(Kit.Amber.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp),
                fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = Kit.Amber, maxLines = 1)
        }
        if (chosen) Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = Kit.Green)
    }
}

/** Whether the bar's branch switcher is in use for the signed-in person (they may see more than one branch). */
@Composable
internal fun usesBranchScope(): Boolean {
    val user by koinInject<SessionManager>().currentUser.collectAsState()
    return canSeeEveryBranch(user) && BranchScope.restaurantId == user?.restaurantId && BranchScope.branches.size > 1
}

/**
 * Tells a page which branch the bar's switcher looks at: [onBranch] gets the branch's id, or null for every branch. Does
 * nothing for people without the switcher, so pages keep working as before for them.
 */
@Composable
internal fun FollowBranchScope(workspace: Workspace, onBranch: (String?) -> Unit) {
    val user by koinInject<SessionManager>().currentUser.collectAsState()
    val scoped = usesBranchScope()
    val all = BranchScope.isAll(workspace)
    LaunchedEffect(scoped, all, user?.defaultBranchId) {
        if (scoped) onBranch(if (all) null else user?.defaultBranchId)
    }
}

/** The branch a page shows: null for every branch (or for someone without the switcher, who sees what they always saw). */
@Composable
internal fun scopedBranchId(workspace: Workspace): String? {
    val user by koinInject<SessionManager>().currentUser.collectAsState()
    return if (usesBranchScope() && !BranchScope.isAll(workspace)) user?.defaultBranchId else null
}
