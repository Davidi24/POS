package com.saporini.mobile_desktop.workspace.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse
import com.saporini.mobile_desktop.auth.ui.login.LoginScreen
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.session.Workspace
import com.saporini.mobile_desktop.core.session.accessibleWorkspaces
import com.saporini.mobile_desktop.core.theme.Inter
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.pos_simple_logo
import mobile_desktop.shared.generated.resources.workspace_admin
import mobile_desktop.shared.generated.resources.workspace_kds
import mobile_desktop.shared.generated.resources.workspace_pos
import mobile_desktop.shared.generated.resources.workspace_statistics
import mobile_desktop.shared.generated.resources.workspace_fraud
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

private val Green = Color(0xFF4F7942)
private val PageBackground = Color(0xFFFCFCFB)
private val CardBackground = Color(0xFFFCFCFB)
private val DecorationFill = Color(0xFFEFF3EE)
private val DecorationLine = Color(0xFFA9B8A6)
private val CardLeaf = Color(0xFFEEF3ED)
private val IconTile = Color(0xFFEEF3EE)
private val IconGreen = Color(0xFF3F6B3A)
private val TitleInk = Color(0xFF151816)
private val GreenDark = Color(0xFF45693A)
private val GreenSoft = Color(0xFFEEF3EC)
private val Ink = Color(0xFF1F2322)
private val Muted = Color(0xFF6F736E)
private val CardBorder = Color(0xFFE6E9E4)

// Shown only when the user can open two or more workspaces; with one, the app opens it directly.
object WorkspacePickerScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val sessionManager = koinInject<SessionManager>()
        val screenModel = koinInject<WorkspaceScreenModel>()
        val state by screenModel.state.collectAsState()
        val user by sessionManager.currentUser.collectAsState()
        val workspaces = user?.let { accessibleWorkspaces(it) }.orEmpty().sortedBy { it.ordinal }

        LaunchedEffect(state.loggedOut) {
            if (state.loggedOut) navigator.replaceAll(LoginScreen)
        }

        BoxWithConstraints(Modifier.fillMaxSize().background(PageBackground)) {
            val narrow = maxWidth < 760.dp
            val fitsFourInRow = maxWidth >= 1240.dp
            val twoRows = workspaces.size > 3 && Workspace.FRAUD_DETECTION in workspaces
            val five = workspaces.size == 5
            // Five choices get smaller cards so the two rows sit comfortably on one screen.
            val threeColumnCardWidth = ((maxWidth - 104.dp) / 3).coerceAtMost(if (five) 236.dp else 300.dp)
            PageDecorations(Modifier.fillMaxSize(), narrow)
            val windowHeight = maxHeight
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    // Two rows of cards sit in the middle of the window (lower than the top), still scrolling on short windows.
                    .then(if (twoRows && !narrow) Modifier.heightIn(min = windowHeight) else Modifier)
                    .padding(horizontal = if (narrow) 16.dp else 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (twoRows && !narrow) Arrangement.Center else Arrangement.Top
            ) {
                Spacer(Modifier.height(if (narrow) 84.dp else if (twoRows) 90.dp else 150.dp))
                // "Welcome, David" in black, then "Choose your workspace" smaller and grey.
                val firstName = user?.firstName?.trim().orEmpty().ifBlank { user?.username.orEmpty() }
                Text(
                    if (firstName.isBlank()) "Welcome" else "Welcome, $firstName",
                    fontFamily = Inter(), fontWeight = FontWeight.Bold,
                    fontSize = if (narrow) 26.sp else 38.sp, color = TitleInk,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(if (narrow) 4.dp else 8.dp))
                Text(
                    "Choose your workspace",
                    fontFamily = Inter(), fontWeight = FontWeight.Medium,
                    fontSize = if (narrow) 16.sp else 20.sp, color = Muted,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(if (narrow || twoRows) 24.dp else 44.dp))
                if (narrow) {
                    Column(Modifier.widthIn(max = 440.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        workspaces.forEach { workspace ->
                            WorkspaceCard(workspace, Modifier.fillMaxWidth(), compact = true) { navigator.push(screenFor(workspace)) }
                        }
                    }
                } else if (twoRows) {
                    val cardWidth = threeColumnCardWidth
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(if (five) 16.dp else 20.dp)
                    ) {
                        workspaces.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(if (five) 16.dp else 20.dp)) {
                                row.forEach { workspace ->
                                    WorkspaceCard(workspace, Modifier.width(cardWidth).height(if (five) 212.dp else 272.dp), compact = true, dense = true, smallFive = five) {
                                        navigator.push(screenFor(workspace))
                                    }
                                }
                            }
                        }
                    }
                } else if (workspaces.size <= 3 || fitsFourInRow) {
                    // Up to three cards, or four when the window is wide enough, sit in one row.
                    // Four cards (super admin) are a little smaller than the usual three.
                    val four = workspaces.size >= 4
                    Row(horizontalArrangement = Arrangement.spacedBy(if (four) 18.dp else 24.dp)) {
                        workspaces.forEach { workspace ->
                            WorkspaceCard(
                                workspace,
                                Modifier.width(if (four) 256.dp else 326.dp).height(if (four) 292.dp else 318.dp),
                                compact = false,
                                dense = four
                            ) { navigator.push(screenFor(workspace)) }
                        }
                    }
                } else {
                    // Four cards on a narrower window: two rows of two.
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        workspaces.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                pair.forEach { workspace ->
                                    WorkspaceCard(workspace, Modifier.width(290.dp).height(292.dp), compact = false, dense = true) { navigator.push(screenFor(workspace)) }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(if (narrow || twoRows) 24.dp else 72.dp))
            }
            // Logo on the left; notifications and the profile menu (with log out) on the right.
            WorkspaceTopBar(
                user = user,
                loggingOut = state.isLoggingOut,
                onLogout = screenModel::logout,
                compact = narrow
            )
        }
    }
}

// Soft green shapes in the top-right and bottom-left corners, each with a thin curved line.
@Composable
private fun PageDecorations(modifier: Modifier, narrow: Boolean) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val k = if (narrow) 0.55f else 1f
        // Top-right shape.
        val tr = Path().apply {
            moveTo(w - 232.dp.toPx() * k, 0f)
            cubicTo(w - 222.dp.toPx() * k, 70.dp.toPx() * k, w - 170.dp.toPx() * k, 112.dp.toPx() * k, w - 92.dp.toPx() * k, 124.dp.toPx() * k)
            cubicTo(w - 48.dp.toPx() * k, 131.dp.toPx() * k, w - 22.dp.toPx() * k, 160.dp.toPx() * k, w, 190.dp.toPx() * k)
            lineTo(w, 0f); close()
        }
        drawPath(tr, DecorationFill)
        val trLine = Path().apply {
            moveTo(w - 143.dp.toPx() * k, 0f)
            cubicTo(w - 132.dp.toPx() * k, 58.dp.toPx() * k, w - 80.dp.toPx() * k, 105.dp.toPx() * k, w, 134.dp.toPx() * k)
        }
        drawPath(trLine, DecorationLine, style = Stroke(width = 1.dp.toPx()))
        // Bottom-left shape.
        val bl = Path().apply {
            moveTo(0f, h - 214.dp.toPx() * k)
            cubicTo(50.dp.toPx() * k, h - 214.dp.toPx() * k, 86.dp.toPx() * k, h - 170.dp.toPx() * k, 116.dp.toPx() * k, h - 108.dp.toPx() * k)
            cubicTo(146.dp.toPx() * k, h - 46.dp.toPx() * k, 196.dp.toPx() * k, h - 16.dp.toPx() * k, 286.dp.toPx() * k, h)
            lineTo(0f, h); close()
        }
        drawPath(bl, DecorationFill)
        val blLine = Path().apply {
            moveTo(0f, h - 124.dp.toPx() * k)
            cubicTo(56.dp.toPx() * k, h - 116.dp.toPx() * k, 110.dp.toPx() * k, h - 70.dp.toPx() * k, 134.dp.toPx() * k, h)
        }
        drawPath(blLine, DecorationLine, style = Stroke(width = 1.dp.toPx()))
    }
}

@Composable
private fun WorkspaceTopBar(user: CurrentUserResponse?, loggingOut: Boolean, onLogout: () -> Unit, compact: Boolean) {
    Row(
        Modifier.fillMaxWidth().height(if (compact) 60.dp else 76.dp).padding(horizontal = if (compact) 14.dp else 28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(Res.drawable.pos_simple_logo),
            contentDescription = "Saporini",
            modifier = Modifier.size(width = if (compact) 30.dp else 40.dp, height = if (compact) 40.dp else 54.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.weight(1f))
        // Status, language, notifications and profile, ending just before the corner decoration.
        Row(
            Modifier.padding(end = if (compact) 0.dp else 236.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 10.dp)
        ) {
            com.saporini.mobile_desktop.pos.ui.shell.OnlineStatus(textColor = Ink, showLabel = !compact)
            if (!compact) com.saporini.mobile_desktop.pos.ui.shell.LanguageSelector()
            val navigator = LocalNavigator.currentOrThrow
            val center = org.koin.compose.getKoin().getOrNull<com.saporini.mobile_desktop.notifications.NotificationCenter>()
            com.saporini.mobile_desktop.notifications.NotificationBell(
                tint = Ink,
                iconSize = 26.dp,
                onOpen = { notification ->
                    // Open POS on the reservation the notification points at.
                    center?.requestOpen(notification)
                    if (user?.let { Workspace.POS in accessibleWorkspaces(it) } == true) navigator.push(com.saporini.mobile_desktop.pos.ui.PosScreen)
                }
            )
            ProfileMenu(user, loggingOut, onLogout, compact)
        }
    }
}

@Composable
private fun ProfileMenu(user: CurrentUserResponse?, loggingOut: Boolean, onLogout: () -> Unit, compact: Boolean) {
    var open by remember { mutableStateOf(false) }
    val name = user?.let { "${it.firstName} ${it.lastName}".trim().ifBlank { it.username } } ?: "Signed in"
    val initials = user?.let { listOf(it.firstName, it.lastName).mapNotNull { part -> part.firstOrNull()?.uppercaseChar() }.joinToString("") }
        ?.ifBlank { null } ?: "?"
    val role = user?.roles?.firstOrNull()?.lowercase()?.replace('_', ' ')?.replaceFirstChar { it.uppercase() }
    Box {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(50),
            color = Color.White,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Row(
                Modifier.padding(start = 5.dp, end = if (compact) 8.dp else 12.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Green), contentAlignment = Alignment.Center) {
                    Text(initials, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                }
                if (!compact) {
                    Column {
                        Text(name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        role?.let { Text(it, fontFamily = Inter(), fontSize = 12.sp, color = Muted, maxLines = 1) }
                    }
                }
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp), tint = Ink)
            }
        }
        DropdownMenu(
            expanded = open, onDismissRequest = { open = false }, offset = DpOffset(0.dp, 8.dp),
            shape = RoundedCornerShape(12.dp), containerColor = Color.White, shadowElevation = 8.dp
        ) {
            Column(Modifier.width(240.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                user?.email?.let { Text(it, fontFamily = Inter(), fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                role?.let {
                    Text(
                        it, Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).background(GreenSoft).padding(horizontal = 8.dp, vertical = 3.dp),
                        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Green
                    )
                }
            }
            HorizontalDivider(color = CardBorder)
            DropdownMenuItem(
                text = { Text(if (loggingOut) "Logging out…" else "Log out", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFFB13A2F)) },
                leadingIcon = {
                    if (loggingOut) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFFB13A2F))
                    else Icon(Icons.AutoMirrored.Outlined.Logout, null, Modifier.size(18.dp), tint = Color(0xFFB13A2F))
                },
                enabled = !loggingOut,
                onClick = { open = false; onLogout() }
            )
        }
    }
}

@Composable
private fun WorkspaceCard(workspace: Workspace, modifier: Modifier, compact: Boolean, dense: Boolean = false, smallFive: Boolean = false, onOpen: () -> Unit) {
    val (title, subtitle) = when (workspace) {
        Workspace.POS -> "POS" to "Point of Sale \u2014 orders, tables and payments"
        Workspace.KDS -> "KDS" to "Kitchen Display System"
        Workspace.ADMIN -> "Admin Hub" to "Inventory, suppliers, devices and settings"
        Workspace.STATISTICS -> "Statistics" to "Sales, performance and reports"
        Workspace.FRAUD_DETECTION -> "Fraud Detection" to "Suspicious activity and alerts"
    }
    val illustration = when (workspace) {
        Workspace.POS -> Res.drawable.workspace_pos
        Workspace.KDS -> Res.drawable.workspace_kds
        Workspace.ADMIN -> Res.drawable.workspace_admin
        Workspace.STATISTICS -> Res.drawable.workspace_statistics
        Workspace.FRAUD_DETECTION -> Res.drawable.workspace_fraud
    }
    val shape = RoundedCornerShape(if (compact) 14.dp else 16.dp)
    Surface(
        onClick = onOpen,
        modifier = modifier.shadow(10.dp, shape, ambientColor = Color(0x12000000), spotColor = Color(0x14000000)),
        shape = shape,
        color = CardBackground,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Box(Modifier.fillMaxWidth()) {
            // Soft leaf-shaped green in the top-right corner.
            Canvas(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp).size(width = if (smallFive) 78.dp else 94.dp, height = if (smallFive) 62.dp else if (compact) 76.dp else 100.dp)) {
                val w = size.width
                val h = size.height
                val leaf = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w - 10.dp.toPx(), 0f)
                    quadraticTo(w, 0f, w, 10.dp.toPx())
                    lineTo(w, h - 14.dp.toPx())
                    quadraticTo(w, h, w - 14.dp.toPx(), h)
                    cubicTo(w * 0.62f, h, w * 0.52f, h * 0.72f, w * 0.40f, h * 0.52f)
                    cubicTo(w * 0.28f, h * 0.32f, w * 0.12f, h * 0.10f, 0f, 0f)
                    close()
                }
                drawPath(leaf, CardLeaf)
            }
            Column(
                Modifier.fillMaxWidth().padding(
                    start = if (smallFive) 18.dp else 24.dp, end = if (smallFive) 18.dp else 24.dp,
                    top = if (smallFive) 14.dp else if (compact) 18.dp else 26.dp, bottom = if (smallFive) 12.dp else if (compact) 16.dp else 22.dp
                ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(illustration),
                    contentDescription = null,
                    modifier = Modifier.height(if (smallFive) 72.dp else if (compact) 100.dp else if (dense) 116.dp else 132.dp).fillMaxWidth(0.82f),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.height(if (smallFive) 10.dp else if (compact) 12.dp else 18.dp))
                Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (smallFive) 17.sp else if (compact) 19.sp else if (dense) 19.sp else 21.sp, color = TitleInk)
                Spacer(Modifier.height(if (smallFive) 4.dp else 6.dp))
                Text(
                    subtitle, Modifier.fillMaxWidth().padding(horizontal = if (compact) 8.dp else 20.dp).heightIn(min = if (smallFive) 36.dp else if (compact && dense) 60.dp else if (compact) 0.dp else 46.dp),
                    fontFamily = Inter(), fontSize = if (smallFive) 12.sp else if (compact) 14.sp else if (dense) 14.sp else 16.sp,
                    lineHeight = if (smallFive) 17.sp else if (compact || dense) 20.sp else 22.sp,
                    color = Muted, textAlign = TextAlign.Center
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Open $title", Modifier.size(if (smallFive) 22.dp else if (compact) 26.dp else 31.dp), tint = IconGreen)
                }
            }
        }
    }
}

// POS terminal: a slightly tilted screen on a stand.
private val PosTerminalIcon: ImageVector = ImageVector.Builder("PosTerminal", 24.dp, 24.dp, 24f, 24f).apply {
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.4f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(3.4f, 5f); lineTo(20.8f, 5f); lineTo(19.9f, 15.4f); lineTo(2.6f, 15.4f); close()
        moveTo(12f, 15.4f); lineTo(12f, 18.6f)
        moveTo(7.4f, 19.4f); lineTo(16.6f, 19.4f)
    }
    path(fill = SolidColor(Color.Black)) {
        moveTo(17.4f, 6.6f); lineTo(19.3f, 6.6f); lineTo(18.6f, 13.9f); lineTo(16.7f, 13.9f); close()
    }
}.build()

// Chef hat: puffed top over a band.
private val ChefHatIcon: ImageVector = ImageVector.Builder("ChefHat", 24.dp, 24.dp, 24f, 24f).apply {
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.4f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(6.6f, 13.6f)
        curveTo(4.6f, 13.1f, 3.5f, 11.3f, 3.9f, 9.5f)
        curveTo(4.3f, 7.5f, 6.4f, 6.3f, 8.3f, 6.9f)
        curveTo(8.9f, 5.0f, 10.4f, 3.9f, 12f, 3.9f)
        curveTo(13.6f, 3.9f, 15.1f, 5.0f, 15.7f, 6.9f)
        curveTo(17.6f, 6.3f, 19.7f, 7.5f, 20.1f, 9.5f)
        curveTo(20.5f, 11.3f, 19.4f, 13.1f, 17.4f, 13.6f)
        lineTo(17.4f, 19.6f); lineTo(6.6f, 19.6f); close()
        moveTo(6.6f, 16.4f); lineTo(17.4f, 16.4f)
    }
}.build()
