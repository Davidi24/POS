package com.saporini.mobile_desktop.platform.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddToPhotos
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.SpaceBar
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.composed
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitSwitch
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.i18n.Text
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.Barcode
import com.saporini.mobile_desktop.core.ui.QrCode
import com.saporini.mobile_desktop.core.ui.RemoteImage
import com.saporini.mobile_desktop.platform.Backdrop
import com.saporini.mobile_desktop.platform.BlockAlign
import com.saporini.mobile_desktop.platform.BlockKind
import com.saporini.mobile_desktop.platform.MAX_RECEIPT_BLOCK_TEXT
import com.saporini.mobile_desktop.platform.MAX_RECEIPT_TEXT
import com.saporini.mobile_desktop.platform.ReceiptBlock
import com.saporini.mobile_desktop.platform.ReceiptLineStyle
import com.saporini.mobile_desktop.platform.ReceiptDesign
import com.saporini.mobile_desktop.platform.defaultReceipt
import com.saporini.mobile_desktop.platform.ReceiptLooks
import com.saporini.mobile_desktop.platform.ReceiptQr
import com.saporini.mobile_desktop.platform.RestaurantDetailsScreenModel
import com.saporini.mobile_desktop.platform.RestaurantDetailsState
import com.saporini.mobile_desktop.platform.TextSize
import com.saporini.mobile_desktop.platform.effectiveAt
import com.saporini.mobile_desktop.platform.newBlock
import com.saporini.mobile_desktop.platform.newBlockId
import com.saporini.mobile_desktop.platform.qrLink
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Info
import com.saporini.mobile_desktop.platform.dgiiTimbre
import com.saporini.mobile_desktop.platform.FiscalType
import com.saporini.mobile_desktop.platform.ReceiptKind
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.math.roundToInt

private val PaperColor = Color(0xFFFFFEFA)
private val PrintInk = Color(0xFF222426)
private val PaperWidth = 300.dp

/** What's chosen on the canvas: a block or a picture behind. */
private sealed interface Picked {
    data class Block(val id: String) : Picked
    data class Behind(val id: String) : Picked
}

/** What the picture picker is for. */
private sealed interface PictureFor {
    data object NewBlock : PictureFor
    data object NewBehind : PictureFor
    data class Block(val id: String) : PictureFor
}

/** The receipt's facts, worked out once for the paper. */
private class ReceiptFacts(
    val name: String, val legalName: String, val address: String?, val phone: String?, val vat: String?, val currency: String,
    val logoUrl: String?, val topText: String, val bottomText: String, val website: String?, val social: com.saporini.mobile_desktop.platform.SocialLinks,
    // Which receipt is shown, and (fiscal) its e-CF type; the restaurant's RNC (tax number) for DGII.
    val receipt: ReceiptKind = ReceiptKind.PRELIMINARY, val fiscalType: FiscalType = FiscalType.E32, val rnc: String? = null
)

/**
 * The receipt as a small canvas (user, 2026-11-23: "I can add an image, a line or a text and move it wherever I want; a
 * barcode, a QR code; built-in things like the dishes or the subtotal; make it much better"): on the left what can be
 * added and the chosen thing's settings, on the right the paper on a dotted board. A click picks a block, dragging it moves
 * it up or down the receipt; pictures behind are dragged anywhere on the paper. The board itself drags and zooms.
 */
@Composable
internal fun ReceiptDesigner(state: RestaurantDetailsState, model: RestaurantDetailsScreenModel, wide: Boolean, compact: Boolean) {
    val restaurant = state.restaurant ?: return
    val restaurantId = restaurant.id
    // The two receipts (user, 2026-11-23): the preliminary bill and the fiscal invoice, each designed on its own.
    var receipt by remember { mutableStateOf(ReceiptKind.PRELIMINARY) }
    val receiptNow by androidx.compose.runtime.rememberUpdatedState(receipt)
    var fiscalType by remember { mutableStateOf(FiscalType.E32) }
    val look = ReceiptLooks.of(restaurantId).of(receipt)
    val change = { f: (ReceiptDesign) -> ReceiptDesign ->
        val whole = ReceiptLooks.of(restaurantId)
        ReceiptLooks.set(restaurantId, whole.with(receiptNow, f(whole.of(receiptNow))))
    }
    var picked by remember(restaurantId) { mutableStateOf<Picked?>(null) }
    var pictureFor by remember { mutableStateOf<PictureFor?>(null) }
    // Copied to be pasted, also on the other receipt (user, 2026-11-23: "I can copy a thing and paste it on the other").
    var clipboard by remember { mutableStateOf<ReceiptBlock?>(null) }
    val editable = state.canEdit
    val facts = factsOf(state, receipt, fiscalType)
    val actions = BlockActions(change, { receiptNow }, { ReceiptLooks.of(restaurantId).of(receiptNow).blocks }, { clipboard }, { clipboard = it },
        { picked = it })
    // A picked picture is uploaded at once (its link goes in the design, saved later with the rest).
    val pick = rememberPicturePicker({ file ->
        val image = runCatching { file.bytes.decodeToImageBitmap() }.getOrNull()
        val target = pictureFor
        pictureFor = null
        if (image == null) return@rememberPicturePicker model.logoProblem("This file can't be opened as a picture.")
        val aspect = image.width.toFloat() / image.height.coerceAtLeast(1)
        model.uploadReceiptPicture(file.bytes, file.fileName, file.contentType) { url ->
            when (target) {
                PictureFor.NewBlock -> {
                    val block = newBlock(BlockKind.PICTURE, url, aspect)
                    change { it.copy(blocks = insertAfter(it.blocks, (picked as? Picked.Block)?.id, block)) }
                    picked = Picked.Block(block.id)
                }
                PictureFor.NewBehind -> {
                    val behind = Backdrop(newBlockId(), url, aspect)
                    change { it.copy(backdrops = it.backdrops + behind) }
                    picked = Picked.Behind(behind.id)
                }
                is PictureFor.Block -> change { l -> l.copy(blocks = l.blocks.map { if (it.id == target.id) it.copy(imageUrl = url, aspect = aspect) else it }) }
                null -> {}
            }
        }
    }, model::logoProblem)
    val choosePicture = { target: PictureFor -> pictureFor = target; pick() }
    val add = { kind: BlockKind ->
        if (kind == BlockKind.PICTURE) choosePicture(PictureFor.NewBlock)
        else {
            val block = newBlock(kind).let { b ->
                if (kind != BlockKind.QR) b
                else {
                    val start = ReceiptQr.entries.firstOrNull { it != ReceiptQr.OWN && qrLink(it, facts.website, facts.social).isNotBlank() } ?: ReceiptQr.OWN
                    b.copy(qr = start, link = qrLink(start, facts.website, facts.social))
                }
            }
            change { it.copy(blocks = insertAfter(it.blocks, (picked as? Picked.Block)?.id, block)) }
            picked = Picked.Block(block.id)
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalBlockActions provides actions) {
    InfoSection("Receipt", "Add what you want, click it to change it, drag it to move it, right-click it to copy or paste. The top and bottom texts are saved with the bar; the rest stays on this device for now.",
        icon = Icons.Outlined.Receipt, compact = compact) {
        val panel: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                AddPanel(look, receipt, clipboard, add, { choosePicture(PictureFor.NewBehind) },
                    paste = { copied ->
                        val block = copied.copy(id = newBlockId())
                        change { it.copy(blocks = insertAfter(it.blocks, (picked as? Picked.Block)?.id, block)) }
                        picked = Picked.Block(block.id)
                    },
                    startAgain = { change { ReceiptDesign(defaultReceipt(receiptNow)) }; picked = null })
                SettingsPanel(state, model, look, picked, change, { picked = it }, { choosePicture(PictureFor.Block(it)) }, facts, { clipboard = it })
            }
        }
        val canvas: @Composable (Modifier) -> Unit = { m ->
            ReceiptCanvas(look, facts, picked, editable, { picked = it }, change, m, compact, { receipt = it; picked = null }, { fiscalType = it })
        }
        // Layers (user, 2026-11-23: "show the layers so I can select something from there").
        val layers: @Composable (Modifier) -> Unit = { m -> LayersPanel(look, facts, picked, { picked = it }, change, m) }
        if (!editable) canvas(Modifier.fillMaxWidth())
        else if (wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
            panel(Modifier.width(330.dp))
            canvas(Modifier.weight(1f))
            layers(Modifier.width(250.dp).height(if (compact) 620.dp else 860.dp))
        } else {
            canvas(Modifier.fillMaxWidth())
            layers(Modifier.fillMaxWidth().height(420.dp))
            panel(Modifier.fillMaxWidth())
        }
    }
    }
}

/**
 * What a right click (or its keyboard shortcut) does to a block (user, 2026-11-23: "I copy and paste by right-clicking"):
 * copy (Ctrl+C), paste under it (Ctrl+V, also on the other receipt), duplicate (Ctrl+D), move, hide, remove (Delete).
 */
private class BlockActions(
    val change: ((ReceiptDesign) -> ReceiptDesign) -> Unit,
    val receipt: () -> ReceiptKind,
    val blocks: () -> List<ReceiptBlock>,
    val clipboard: () -> ReceiptBlock?,
    val setClipboard: (ReceiptBlock) -> Unit,
    val pick: (Picked?) -> Unit
) {
    fun copy(block: ReceiptBlock) { if (!block.kind.required) setClipboard(block) }
    fun canPaste(): Boolean = clipboard()?.let { it.kind.allowedOn(receipt()) && !it.kind.required } == true
    fun paste(afterId: String?) {
        val copied = clipboard()?.takeIf { canPaste() } ?: return
        val block = copied.copy(id = newBlockId())
        change { it.copy(blocks = insertAfter(it.blocks, afterId, block)) }
        pick(Picked.Block(block.id))
    }
    fun duplicate(block: ReceiptBlock) {
        if (block.kind.required) return
        val copy = block.copy(id = newBlockId())
        change { it.copy(blocks = insertAfter(it.blocks, block.id, copy)) }
        pick(Picked.Block(copy.id))
    }
    fun move(block: ReceiptBlock, by: Int) = change { l ->
        val i = l.blocks.indexOfFirst { it.id == block.id }
        val j = (i + by).coerceIn(0, l.blocks.lastIndex)
        if (i < 0 || i == j) l else l.copy(blocks = l.blocks.toMutableList().apply { add(j, removeAt(i)) })
    }
    fun toggle(block: ReceiptBlock) = change { l -> l.copy(blocks = l.blocks.map { if (it.id == block.id) it.copy(visible = !it.visible) else it }) }
    fun remove(block: ReceiptBlock) {
        if (block.kind.required) return
        change { l -> l.copy(blocks = l.blocks.filter { it.id != block.id }) }
        pick(null)
    }
}

private val LocalBlockActions = androidx.compose.runtime.staticCompositionLocalOf<BlockActions?> { null }

/** A right click (the second button) at the pointer; [first] catches it before the clicks and drags under it. */
private fun Modifier.onRightClick(first: Boolean, onClick: (Offset) -> Unit): Modifier = this.then(androidx.compose.ui.Modifier).composed {
    val handler by androidx.compose.runtime.rememberUpdatedState(onClick)
    pointerInput(first) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(if (first) androidx.compose.ui.input.pointer.PointerEventPass.Initial else androidx.compose.ui.input.pointer.PointerEventPass.Main)
                if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Press && event.buttons.isSecondaryPressed &&
                    event.changes.none { it.isConsumed }) {
                    event.changes.forEach { it.consume() }
                    handler(event.changes.first().position)
                }
            }
        }
    }
}

/** The right-click menu of a block, or (no block) of the paper: paste at the end. */
@Composable
private fun BlockMenu(block: ReceiptBlock?, onDismiss: () -> Unit) {
    val actions = LocalBlockActions.current ?: return
    androidx.compose.material3.DropdownMenu(true, onDismiss, containerColor = Color.White, shape = RoundedCornerShape(12.dp), shadowElevation = 8.dp) {
        val line = @Composable { label: String, icon: ImageVector, keys: String?, enabled: Boolean, danger: Boolean, run: () -> Unit ->
            androidx.compose.material3.DropdownMenuItem(
                leadingIcon = { Icon(icon, null, Modifier.size(17.dp), tint = if (!enabled) Kit.Faint else if (danger) Kit.Danger else Kit.Ink) },
                text = { Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp,
                    color = if (!enabled) Kit.Faint else if (danger) Kit.Danger else Kit.Ink) },
                trailingIcon = keys?.let { { Text(it, fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Faint, translate = false) } },
                enabled = enabled, onClick = { run(); onDismiss() })
        }
        if (block == null) {
            line("Paste", Icons.Outlined.ContentPaste, "Ctrl+V", actions.canPaste(), false) { actions.paste(null) }
        } else {
            val list = actions.blocks()
            val i = list.indexOfFirst { it.id == block.id }
            val free = !block.kind.required
            if (free) line("Copy", Icons.Outlined.ContentCopy, "Ctrl+C", true, false) { actions.copy(block) }
            line("Paste below", Icons.Outlined.ContentPaste, "Ctrl+V", actions.canPaste(), false) { actions.paste(block.id) }
            if (free) line("Duplicate", Icons.Outlined.AddToPhotos, "Ctrl+D", true, false) { actions.duplicate(block) }
            androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Kit.RowBorder)
            line("Move up", Icons.Outlined.ArrowUpward, null, i > 0, false) { actions.move(block, -1) }
            line("Move down", Icons.Outlined.ArrowDownward, null, i in 0 until list.lastIndex, false) { actions.move(block, 1) }
            line(if (block.visible) "Hide" else "Show", if (block.visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null, true, false) { actions.toggle(block) }
            if (free) {
                androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Kit.RowBorder)
                line("Remove", Icons.Outlined.Delete, "Delete", true, true) { actions.remove(block) }
            }
        }
    }
}

private fun insertAfter(blocks: List<ReceiptBlock>, afterId: String?, block: ReceiptBlock): List<ReceiptBlock> {
    val at = blocks.indexOfFirst { it.id == afterId }
    return if (at < 0) blocks + block else blocks.take(at + 1) + block + blocks.drop(at + 1)
}

private fun factsOf(state: RestaurantDetailsState, receipt: ReceiptKind, fiscalType: FiscalType): ReceiptFacts {
    val restaurant = state.restaurant!!
    val branch = state.branches.firstOrNull { it.id == state.branchId } ?: state.branches.firstOrNull { it.open } ?: state.branches.firstOrNull()
    val address = branch?.let { state.branchAddresses[it.id].orEmpty().firstOrNull() } ?: state.addresses.firstOrNull { it.isPrimary } ?: state.addresses.firstOrNull()
    val tax = state.taxProfiles.firstOrNull { it.isDefault && it.effectiveAt(kotlin.time.Clock.System.now()) }
    return ReceiptFacts(restaurant.name, restaurant.legalName, address?.oneLine, branch?.phone?.takeIf { it.isNotBlank() } ?: restaurant.phone, tax?.vatNumber,
        restaurant.currency, state.branding?.logoUrl, state.brandingDraft.receiptHeader, state.brandingDraft.receiptFooter,
        state.details?.website ?: restaurant.website, state.details?.social ?: com.saporini.mobile_desktop.platform.socialOf(restaurant), receipt, fiscalType,
        tax?.taxNumber?.takeIf { it.isNotBlank() } ?: tax?.vatNumber?.takeIf { it.isNotBlank() })
}

// ---- Adding ----

private fun kindIcon(kind: BlockKind): ImageVector = when (kind) {
    BlockKind.TEXT -> Icons.Outlined.TextFields
    BlockKind.LINE -> Icons.Outlined.HorizontalRule
    BlockKind.PICTURE -> Icons.Outlined.Image
    BlockKind.BARCODE -> Icons.Outlined.ViewWeek
    BlockKind.QR -> Icons.Outlined.QrCode2
    BlockKind.SPACE -> Icons.Outlined.SpaceBar
    BlockKind.LOGO -> Icons.Outlined.Image
    BlockKind.RESTAURANT -> Icons.Outlined.Storefront
    BlockKind.TOP_TEXT, BlockKind.BOTTOM_TEXT -> Icons.Outlined.Notes
    BlockKind.ORDER -> Icons.Outlined.ReceiptLong
    BlockKind.ITEMS -> Icons.Outlined.RestaurantMenu
    BlockKind.SUBTOTAL -> Icons.Outlined.Calculate
    BlockKind.VAT -> Icons.Outlined.Percent
    BlockKind.TOTAL -> Icons.Outlined.Functions
    BlockKind.PAYMENT -> Icons.Outlined.Payments
    BlockKind.THANKS -> Icons.Outlined.FavoriteBorder
    BlockKind.PRELIM_NOTICE -> Icons.Outlined.Info
    BlockKind.FISCAL -> Icons.Outlined.Verified
    BlockKind.DGII_QR -> Icons.Outlined.QrCode2
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddPanel(look: ReceiptDesign, receipt: ReceiptKind, clipboard: ReceiptBlock?, add: (BlockKind) -> Unit, addBehind: () -> Unit,
                     paste: (ReceiptBlock) -> Unit, startAgain: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PanelTitle("Add to the receipt")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BlockKind.entries.filter { !it.builtIn }.forEach { kind -> AddTile(kindIcon(kind), kind.label) { add(kind) } }
            AddTile(Icons.Outlined.Wallpaper, "Picture behind", addBehind)
        }
        PanelTitle("Built in", "Filled in by the till for each order")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BlockKind.entries.filter { it.builtIn && !it.required && it.allowedOn(receipt) }.forEach { kind ->
                val there = look.blocks.any { it.kind == kind }
                Row(Modifier.height(32.dp).clip(RoundedCornerShape(50)).background(if (there) Kit.GreenSoft else Color.White)
                    .border(1.dp, if (there) Kit.Green.copy(alpha = 0.4f) else Kit.Border, RoundedCornerShape(50)).clickable { add(kind) }
                    .padding(start = 9.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(14.dp), tint = Kit.Green)
                    Text(kind.label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink, maxLines = 1)
                }
            }
        }
        LinkText("Start again", startAgain)
    }
}

@Composable
private fun AddTile(icon: ImageVector, label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(
        Modifier.width(98.dp).clip(RoundedCornerShape(12.dp)).background(if (hovered) Kit.GreenSoft else Color.White)
            .border(1.dp, if (hovered) Kit.Green.copy(alpha = 0.5f) else Kit.Border, RoundedCornerShape(12.dp))
            .hoverable(interaction).clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = Kit.Green)
        Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PanelTitle(title: String, line: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(title.uppercase(), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.8.sp, color = Kit.Muted)
        line?.let { Text(it, fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Faint) }
    }
}

// ---- The chosen thing's settings ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsPanel(state: RestaurantDetailsState, model: RestaurantDetailsScreenModel, look: ReceiptDesign, picked: Picked?,
                          change: ((ReceiptDesign) -> ReceiptDesign) -> Unit, onPicked: (Picked?) -> Unit, choosePicture: (String) -> Unit, facts: ReceiptFacts,
                          onCopy: (ReceiptBlock) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Kit.Canvas).border(1.dp, Kit.Border, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val block = (picked as? Picked.Block)?.let { p -> look.blocks.firstOrNull { it.id == p.id } }
        val behind = (picked as? Picked.Behind)?.let { p -> look.backdrops.firstOrNull { it.id == p.id } }
        when {
            block != null -> {
                val index = look.blocks.indexOf(block)
                val set = { f: (ReceiptBlock) -> ReceiptBlock -> change { l -> l.copy(blocks = l.blocks.map { if (it.id == block.id) f(it) else it }) } }
                fun move(by: Int) = change { l ->
                    val i = l.blocks.indexOfFirst { it.id == block.id }
                    val j = (i + by).coerceIn(0, l.blocks.lastIndex)
                    if (i < 0 || i == j) l else l.copy(blocks = l.blocks.toMutableList().apply { add(j, removeAt(i)) })
                }
                SettingsHead(kindIcon(block.kind), block.kind.label, when {
                    block.kind.required -> "Required by DGII: it can be moved, not removed"
                    block.kind.builtIn -> "Built in"
                    else -> null
                }) {
                    SmallTool(Icons.Outlined.ArrowUpward, "Move up", index > 0) { move(-1) }
                    SmallTool(Icons.Outlined.ArrowDownward, "Move down", index < look.blocks.lastIndex) { move(1) }
                    if (!block.kind.required) {
                        SmallTool(Icons.Outlined.AddToPhotos, "Duplicate", true) {
                            val copy = block.copy(id = newBlockId())
                            change { it.copy(blocks = insertAfter(it.blocks, block.id, copy)) }
                            onPicked(Picked.Block(copy.id))
                        }
                    }
                    if (!block.kind.required) SmallTool(Icons.Outlined.Delete, "Remove", true, danger = true) {
                        change { l -> l.copy(blocks = l.blocks.filter { it.id != block.id }) }
                        onPicked(null)
                    }
                }
                BlockSettings(state, model, block, set, choosePicture, facts)
            }
            behind != null -> {
                val set = { f: (Backdrop) -> Backdrop -> change { l -> l.copy(backdrops = l.backdrops.map { if (it.id == behind.id) f(it) else it }) } }
                SettingsHead(Icons.Outlined.Wallpaper, "Picture behind", null) {
                    SmallTool(Icons.Outlined.Delete, "Remove", true, danger = true) {
                        change { l -> l.copy(backdrops = l.backdrops.filter { it.id != behind.id }) }
                        onPicked(null)
                    }
                }
                Text("Drag it on the receipt to put it anywhere.", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
                SliderLine("Size", behind.width, 0.15f..1f) { v -> set { it.copy(width = v) } }
                SliderLine("Opacity", behind.opacity, 0.05f..1f) { v -> set { it.copy(opacity = v) } }
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.TouchApp, null, Modifier.size(20.dp), tint = Kit.Muted)
                    Text("Click something on the receipt to change it, or drag it to move it.", fontFamily = Inter(), fontSize = 12.5.sp, lineHeight = 17.sp,
                        color = Kit.Muted)
                }
                if (look.backdrops.isNotEmpty()) {
                    PanelTitle("Pictures behind")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        look.backdrops.forEach { b ->
                            RemoteImage(b.imageUrl, null, Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).checkerboard().border(1.dp, Kit.Border, RoundedCornerShape(10.dp))
                                .clickable { onPicked(Picked.Behind(b.id)) }, contentScale = ContentScale.Crop) {}
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockSettings(state: RestaurantDetailsState, model: RestaurantDetailsScreenModel, block: ReceiptBlock, set: ((ReceiptBlock) -> ReceiptBlock) -> Unit,
                          choosePicture: (String) -> Unit, facts: ReceiptFacts) {
    val textLook: @Composable () -> Unit = {
        ChipLine("Size") { TextSize.entries.forEach { s -> ToolChip(s.label, block.size == s) { set { it.copy(size = s) } } } }
        ChipLine("Align") { BlockAlign.entries.forEach { a -> ToolChip(a.label, block.align == a) { set { it.copy(align = a) } } } }
        SwitchLine("Bold", block.bold) { v -> set { it.copy(bold = v) } }
    }
    when (block.kind) {
        BlockKind.TEXT -> {
            TextInput(block.text, { v -> set { it.copy(text = v.take(MAX_RECEIPT_BLOCK_TEXT)) } }, Modifier.fillMaxWidth(), label = "Text", singleLine = false, minLines = 2,
                maxLength = MAX_RECEIPT_BLOCK_TEXT)
            textLook()
        }
        BlockKind.TOP_TEXT, BlockKind.BOTTOM_TEXT -> {
            val top = block.kind == BlockKind.TOP_TEXT
            TextInput(if (top) state.brandingDraft.receiptHeader else state.brandingDraft.receiptFooter,
                { v -> model.changeBranding { if (top) it.copy(receiptHeader = v) else it.copy(receiptFooter = v) } }, Modifier.fillMaxWidth(),
                label = if (top) "Top of the receipt" else "Bottom of the receipt", singleLine = false, minLines = 2, maxLength = MAX_RECEIPT_TEXT,
                placeholder = if (top) "e.g. Thank you for dining with us" else "e.g. Wi-Fi: Saporini · see you soon", hint = "Saved with the bar at the bottom.")
            textLook()
        }
        BlockKind.THANKS -> {
            TextInput(block.text, { v -> set { it.copy(text = v.take(MAX_RECEIPT_BLOCK_TEXT)) } }, Modifier.fillMaxWidth(), label = "Text", maxLength = MAX_RECEIPT_BLOCK_TEXT)
            textLook()
        }
        BlockKind.LINE -> ChipLine("Style") {
            ReceiptLineStyle.entries.forEach { style ->
                val on = block.line == style
                Column(Modifier.width(88.dp).clip(RoundedCornerShape(10.dp)).background(if (on) Kit.GreenSoft else Color.White)
                    .border(if (on) 1.5.dp else 1.dp, if (on) Kit.Green else Kit.Border, RoundedCornerShape(10.dp)).clickable { set { it.copy(line = style) } }
                    .padding(horizontal = 10.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.fillMaxWidth().height(12.dp), Alignment.Center) { ReceiptRule(style, Modifier.fillMaxWidth()) }
                    Text(style.label, fontFamily = Inter(), fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, fontSize = 11.5.sp, color = if (on) Kit.Green else Kit.Ink)
                }
            }
        }
        BlockKind.PICTURE -> {
            ToolButton("Choose another picture", Icons.Outlined.Image) { choosePicture(block.id) }
            SliderLine("Size", block.width, 0.15f..1f) { v -> set { it.copy(width = v) } }
            SliderLine("Opacity", block.opacity, 0.05f..1f) { v -> set { it.copy(opacity = v) } }
            ChipLine("Align") { BlockAlign.entries.forEach { a -> ToolChip(a.label, block.align == a) { set { it.copy(align = a) } } } }
        }
        BlockKind.BARCODE -> {
            ChipLine("It holds") {
                ToolChip("The receipt's number", !block.ownCode) { set { it.copy(ownCode = false) } }
                ToolChip("Your own code", block.ownCode) { set { it.copy(ownCode = true) } }
            }
            if (block.ownCode) TextInput(block.code, { v -> set { it.copy(code = v.take(40)) } }, Modifier.fillMaxWidth(), label = "Code",
                placeholder = "e.g. WIFI-2026", maxLength = 40, hint = "Letters, numbers and signs; no accents.")
            else Text("A real barcode of the receipt's number, so the receipt can be found again by scanning it.", fontFamily = Inter(), fontSize = 12.sp,
                lineHeight = 16.sp, color = Kit.Muted)
            SliderLine("Size", block.width, 0.4f..1f) { v -> set { it.copy(width = v) } }
        }
        BlockKind.QR -> {
            // The link is the QR code's own, always editable (user, 2026-11-23: "can I decide the link or edit it here?"); the chips
            // fill it in with the restaurant's links from Details.
            val current = qrLinkOf(block, facts)
            ChipLine("Fill in from Details") {
                ReceiptQr.entries.filter { it != ReceiptQr.OWN }.forEach { q ->
                    val link = qrLink(q, facts.website, facts.social)
                    ToolChip(q.label, link.isNotBlank() && link == current) { set { it.copy(qr = q, link = link) } }
                }
            }
            TextInput(current, { v -> set { it.copy(qr = ReceiptQr.OWN, link = v.take(300)) } }, Modifier.fillMaxWidth(), label = "Link",
                placeholder = "https://", maxLength = 300, hint = "Any link: your website, a menu, a review page, Wi-Fi…")
            if (block.qr != ReceiptQr.OWN && qrLink(block.qr, facts.website, facts.social).isBlank()) Text("There's no ${block.qr.label} link in Details yet: write the link here.",
                fontFamily = Inter(), fontSize = 12.sp, lineHeight = 16.sp, color = Kit.Amber)
            TextInput(block.text, { v -> set { it.copy(text = v.take(60)) } }, Modifier.fillMaxWidth(), label = "Under it", placeholder = "Scan me", maxLength = 60)
            SliderLine("Size", block.width, 0.2f..0.7f) { v -> set { it.copy(width = v) } }
        }
        BlockKind.SPACE -> SliderLine("Height", block.space / 60f, 0.05f..1f, "${block.space} px") { v -> set { it.copy(space = (v * 60).roundToInt().coerceAtLeast(3)) } }
        BlockKind.LOGO -> {
            Text("The logo is changed at the top of this page.", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
            SliderLine("Size", block.width, 0.12f..0.6f) { v -> set { it.copy(width = v) } }
        }
        BlockKind.ITEMS -> {
            Text("Each dish of the order, as the till prints it. Here with sample dishes.", fontFamily = Inter(), fontSize = 12.sp, lineHeight = 16.sp, color = Kit.Muted)
            OwnWords(block.text, "A heading above the dishes", "e.g. Your order", optional = true) { v -> set { it.copy(text = v) } }
            SwitchLine("Prices", block.prices) { v -> set { it.copy(prices = v) } }
            SwitchLine("Options (extra cheese, no onions)", block.options) { v -> set { it.copy(options = v) } }
            SwitchLine("Ingredients", block.ingredients) { v -> set { it.copy(ingredients = v) } }
            textLook()
        }
        BlockKind.PRELIM_NOTICE -> {
            Text("Printed on every preliminary bill: it has no fiscal number and isn't sent to DGII. The order can still change after it. Its words are DGII's and stay; you can add a line under them.",
                fontFamily = Inter(), fontSize = 12.sp, lineHeight = 16.sp, color = Kit.Muted)
            OwnWords(block.text, "A line under it", "e.g. Ask for your fiscal invoice when you pay", optional = true) { v -> set { it.copy(text = v) } }
        }
        BlockKind.FISCAL -> Text("The e-CF's details on the fiscal invoice: its type (E32 for a consumer, E31 for a business with its RNC), the e-NCF, the restaurant's RNC and the date. DGII isn't connected yet, so the numbers are test ones.",
            fontFamily = Inter(), fontSize = 12.sp, lineHeight = 16.sp, color = Kit.Muted)
        BlockKind.DGII_QR -> Text("DGII's verification QR code, with the security code and the signing date. Guests scan it to check the invoice.",
            fontFamily = Inter(), fontSize = 12.sp, lineHeight = 16.sp, color = Kit.Muted)
        BlockKind.RESTAURANT -> {
            Text("Name, address, phone and VAT number, from the restaurant's details. Change the words if you want; the first line prints bigger.",
                fontFamily = Inter(), fontSize = 12.sp, lineHeight = 16.sp, color = Kit.Muted)
            TextInput(block.text.ifBlank { restaurantLines(facts).joinToString("\n") }, { v -> set { it.copy(text = v.take(MAX_RECEIPT_BLOCK_TEXT)) } },
                Modifier.fillMaxWidth(), label = "Text", singleLine = false, minLines = 4, maxLength = MAX_RECEIPT_BLOCK_TEXT)
            if (block.text.isNotBlank()) LinkText("Use the restaurant's details again") { set { it.copy(text = "") } }
            textLook()
        }
        BlockKind.ORDER -> {
            Text("The receipt's number and time, the table, the guests and the waiter, filled in by the till. The words before them are yours to change.",
                fontFamily = Inter(), fontSize = 12.sp, lineHeight = 16.sp, color = Kit.Muted)
            OrderLabels.forEachIndexed { i, default ->
                TextInput(block.labels.getOrNull(i) ?: default, { v -> set { b -> b.copy(labels = OrderLabels.indices.map { j -> if (j == i) v.take(30) else b.labels.getOrNull(j) ?: OrderLabels[j] }) } },
                    Modifier.fillMaxWidth(), label = OrderLabelNames[i], maxLength = 30)
            }
            textLook()
        }
        BlockKind.SUBTOTAL, BlockKind.VAT, BlockKind.TOTAL -> {
            Text("The amount is filled in by the till; the words before it are yours to change.", fontFamily = Inter(), fontSize = 12.sp, lineHeight = 16.sp, color = Kit.Muted)
            OwnWords(block.text.ifBlank { defaultLabel(block.kind, facts) }, "Words", defaultLabel(block.kind, facts)) { v -> set { it.copy(text = v) } }
            textLook()
        }
        BlockKind.PAYMENT -> {
            Text("How it was paid, filled in by the till (card, cash or both when split).", fontFamily = Inter(), fontSize = 12.sp, lineHeight = 16.sp, color = Kit.Muted)
            OwnWords(block.text.ifBlank { "Change" }, "Words for the change", "Change") { v -> set { it.copy(text = v) } }
            textLook()
        }
        else -> {
            Text("Filled in by the till for each order.", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
            textLook()
        }
    }
}

/** A built-in block's own words, in one line. */
@Composable
private fun OwnWords(value: String, label: String, placeholder: String, optional: Boolean = false, onChange: (String) -> Unit) {
    TextInput(value, { v -> onChange(v.take(60)) }, Modifier.fillMaxWidth(), label = label, placeholder = placeholder, optional = optional, maxLength = 60)
}

/** Order details' words, in order: before the receipt's number, the table, the guests and the waiter. */
private val OrderLabels = listOf("Receipt", "Table", "guests", "Waiter:")
private val OrderLabelNames = listOf("Before the receipt's number", "Before the table", "After the guests", "Before the waiter")

private fun defaultLabel(kind: BlockKind, facts: ReceiptFacts): String = when (kind) {
    BlockKind.SUBTOTAL -> "Subtotal"
    BlockKind.VAT -> "of which VAT 20%"
    BlockKind.TOTAL -> "TOTAL ${facts.currency}"
    else -> ""
}

/** The restaurant's lines as the till prints them. */
private fun restaurantLines(facts: ReceiptFacts): List<String> = listOfNotNull(
    facts.name.uppercase(),
    facts.legalName.takeIf { it.isNotBlank() && it != facts.name },
    facts.address,
    facts.phone?.let { "Tel. $it" },
    facts.vat?.let { "VAT $it" }
)

@Composable
private fun SettingsHead(icon: ImageVector, title: String, tag: String?, tools: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(Kit.GreenSoft), Alignment.Center) { Icon(icon, null, Modifier.size(17.dp), tint = Kit.Green) }
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            tag?.let { Text(it, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted) }
        }
        tools()
    }
}

@Composable
private fun SmallTool(icon: ImageVector, label: String, enabled: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    com.saporini.mobile_desktop.core.ui.HoverTooltip(label) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).border(1.dp, Kit.Border, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick), Alignment.Center) {
            Icon(icon, label, Modifier.size(16.dp), tint = if (!enabled) Kit.Faint else if (danger) Kit.Danger else Kit.Ink)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipLine(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

@Composable
private fun SliderLine(label: String, value: Float, range: ClosedFloatingPointRange<Float>, shown: String? = null, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.width(64.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink)
        Slider(value, onChange, Modifier.weight(1f), valueRange = range,
            colors = SliderDefaults.colors(thumbColor = Kit.Green, activeTrackColor = Kit.Green, inactiveTrackColor = Kit.Border))
        Text(shown ?: "${(value * 100).roundToInt()}%", Modifier.width(44.dp), fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Muted, textAlign = TextAlign.End,
            translate = false)
    }
}

@Composable
private fun SwitchLine(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Kit.Ink)
        KitSwitch(on, onChange, compact = true)
    }
}

@Composable
private fun ToolButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.height(34.dp).clip(RoundedCornerShape(50)).background(Color.White).border(1.dp, Kit.Border, RoundedCornerShape(50))
        .clickable(onClick = onClick).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, Modifier.size(16.dp), tint = Kit.Ink)
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Kit.Ink, maxLines = 1)
    }
}

@Composable
private fun ToolChip(text: String, on: Boolean, onClick: () -> Unit) {
    Text(text, Modifier.clip(RoundedCornerShape(50)).background(if (on) Kit.Green else Color.White)
        .border(1.dp, if (on) Kit.Green else Kit.Border, RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (on) Color.White else Kit.Ink, maxLines = 1)
}

/** What a QR code opens: its own link, else (blocks made before the field) the restaurant's link it was set to. */
private fun qrLinkOf(block: ReceiptBlock, facts: ReceiptFacts): String =
    block.link.trim().ifBlank { if (block.qr == ReceiptQr.OWN) "" else qrLink(block.qr, facts.website, facts.social) }

// ---- Layers ----

/** A short line about a block for its layer: its words, its style, what it opens. */
private fun layerLine(block: ReceiptBlock, facts: ReceiptFacts): String? = when (block.kind) {
    BlockKind.TEXT, BlockKind.THANKS -> block.text.ifBlank { null }
    BlockKind.TOP_TEXT -> facts.topText.ifBlank { null }
    BlockKind.BOTTOM_TEXT -> facts.bottomText.ifBlank { null }
    BlockKind.LINE -> block.line.label
    BlockKind.QR -> qrLinkOf(block, facts).ifBlank { null }
    BlockKind.BARCODE -> if (block.ownCode) block.code.ifBlank { null } else "R1042"
    BlockKind.SPACE -> "${block.space} px"
    BlockKind.ITEMS -> listOfNotNull("Prices".takeIf { block.prices }, "Options".takeIf { block.options }, "Ingredients".takeIf { block.ingredients })
        .joinToString(" · ").ifBlank { null }
    else -> null
}

/**
 * Every block of the receipt from top to bottom, and the pictures behind, like a design tool's layers: a click picks it
 * (and shows its settings), the eye switches it off and on, the arrows move it.
 */
@Composable
private fun LayersPanel(look: ReceiptDesign, facts: ReceiptFacts, picked: Picked?, onPicked: (Picked?) -> Unit,
                        change: ((ReceiptDesign) -> ReceiptDesign) -> Unit, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.dp, Kit.Border, RoundedCornerShape(14.dp))) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Layers, null, Modifier.size(17.dp), tint = Kit.Green)
            Spacer(Modifier.width(8.dp))
            Text("Layers", Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink)
            Text("${look.blocks.size + look.backdrops.size}", Modifier.clip(RoundedCornerShape(50)).background(Kit.Tint).padding(horizontal = 8.dp, vertical = 2.dp),
                fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Kit.Muted, translate = false)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Kit.RowBorder))
        com.saporini.mobile_desktop.core.components.ScrollColumn(Modifier.fillMaxWidth().weight(1f).padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            look.blocks.forEachIndexed { index, block ->
                val chosen = picked == Picked.Block(block.id)
                LayerRow(kindIcon(block.kind), block.kind.label, layerLine(block, facts), chosen, block.visible,
                    onClick = { onPicked(Picked.Block(block.id)) },
                    onEye = { change { l -> l.copy(blocks = l.blocks.map { if (it.id == block.id) it.copy(visible = !it.visible) else it }) } },
                    onUp = if (index > 0) ({ change { l -> l.copy(blocks = l.blocks.toMutableList().apply { add(index - 1, removeAt(index)) }) } }) else null,
                    onDown = if (index < look.blocks.lastIndex) ({ change { l -> l.copy(blocks = l.blocks.toMutableList().apply { add(index + 1, removeAt(index)) }) } }) else null,
                    block = block)
            }
            if (look.backdrops.isNotEmpty()) {
                Text("BEHIND EVERYTHING", Modifier.padding(start = 8.dp, top = 10.dp, bottom = 4.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.5.sp,
                    letterSpacing = 0.7.sp, color = Kit.Muted)
                look.backdrops.forEach { b ->
                    LayerRow(Icons.Outlined.Wallpaper, "Picture behind", "${(b.opacity * 100).roundToInt()}%", picked == Picked.Behind(b.id), true,
                        onClick = { onPicked(Picked.Behind(b.id)) }, onEye = null, onUp = null, onDown = null, picture = b.imageUrl)
                }
            }
        }
    }
}

@Composable
private fun LayerRow(icon: ImageVector, title: String, line: String?, chosen: Boolean, visible: Boolean, onClick: () -> Unit, onEye: (() -> Unit)?,
                     onUp: (() -> Unit)?, onDown: (() -> Unit)?, picture: String? = null, block: ReceiptBlock? = null) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var menuAt by remember { mutableStateOf<Offset?>(null) }
    Box {
    Row(
        Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(9.dp))
            .then(if (block != null) Modifier.onRightClick(first = true) { onClick(); menuAt = it } else Modifier)
            .background(when { chosen -> Kit.GreenSoft; hovered -> Kit.Canvas; else -> Color.Transparent })
            .then(if (chosen) Modifier.border(1.dp, Kit.Green.copy(alpha = 0.5f), RoundedCornerShape(9.dp)) else Modifier)
            .hoverable(interaction).clickable(onClick = onClick).padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (picture != null) RemoteImage(picture, null, Modifier.size(24.dp).clip(RoundedCornerShape(5.dp)).checkerboard(), contentScale = ContentScale.Crop) {}
        else Icon(icon, null, Modifier.size(17.dp), tint = if (chosen) Kit.Green else if (visible) Kit.Muted else Kit.Faint)
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Inter(), fontWeight = if (chosen) FontWeight.Bold else FontWeight.SemiBold, fontSize = 12.5.sp,
                color = if (visible) Kit.Ink else Kit.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            line?.let { Text(it, fontFamily = Inter(), fontSize = 10.5.sp, color = Kit.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis, translate = false) }
        }
        // Arrows while pointed at or chosen; the eye always (off shows it's hidden).
        if (hovered || chosen) {
            onUp?.let { LayerTool(Icons.Outlined.KeyboardArrowUp, "Move up", it) }
            onDown?.let { LayerTool(Icons.Outlined.KeyboardArrowDown, "Move down", it) }
        }
        onEye?.let { LayerTool(if (visible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, if (visible) "Hide" else "Show", it,
            tint = if (visible) Kit.Muted else Kit.Faint) }
    }
    if (block != null) menuAt?.let { at ->
        Box(Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }.size(1.dp)) { BlockMenu(block) { menuAt = null } }
    }
    }
}

@Composable
private fun LayerTool(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = Kit.Muted) {
    Box(Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick), Alignment.Center) {
        Icon(icon, label, Modifier.size(16.dp), tint = tint)
    }
}

// ---- The canvas ----

@Composable
private fun ReceiptCanvas(look: ReceiptDesign, facts: ReceiptFacts, picked: Picked?, editable: Boolean, onPicked: (Picked?) -> Unit,
                          change: ((ReceiptDesign) -> ReceiptDesign) -> Unit, modifier: Modifier, compact: Boolean,
                          onReceipt: (ReceiptKind) -> Unit, onFiscalType: (FiscalType) -> Unit) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var boardHeight by remember { mutableStateOf(0) }
    var paperHeight by remember { mutableStateOf(0) }
    val room = with(LocalDensity.current) { 150.dp.toPx() }
    // Fit: the whole receipt in sight, however long it grew (user, 2026-11-23: "things go down and I can't grab them").
    val fit = { zoom = if (paperHeight > 0 && boardHeight > 0) ((boardHeight - room) / paperHeight).coerceIn(0.5f, 1f) else 1f; pan = Offset.Zero }
    val actions = LocalBlockActions.current
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    // Picking something gives the board the keyboard, for Ctrl+C / Ctrl+V / Ctrl+D / Delete.
    val select = { p: Picked? -> onPicked(p); runCatching { focus.requestFocus() }; Unit }
    var boardMenu by remember { mutableStateOf<Offset?>(null) }
    Box(
        modifier.height(if (compact) 620.dp else 860.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFF3F5F2))
            .onPreviewKeyEvent { event ->
                if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown || actions == null || !editable) return@onPreviewKeyEvent false
                val ctrl = event.isCtrlPressed || event.isMetaPressed
                val block = (picked as? Picked.Block)?.let { p -> look.blocks.firstOrNull { it.id == p.id } }
                when {
                    ctrl && event.key == androidx.compose.ui.input.key.Key.C && block != null -> { actions.copy(block); true }
                    ctrl && event.key == androidx.compose.ui.input.key.Key.V -> { actions.paste(block?.id); true }
                    ctrl && event.key == androidx.compose.ui.input.key.Key.D && block != null -> { actions.duplicate(block); true }
                    (event.key == androidx.compose.ui.input.key.Key.Delete || event.key == androidx.compose.ui.input.key.Key.Backspace) && block != null -> { actions.remove(block); true }
                    else -> false
                }
            }
            .focusRequester(focus).focusable()
            .onSizeChanged { boardHeight = it.height }
            .border(1.dp, Kit.Border, RoundedCornerShape(14.dp))
            .drawBehind {
                val step = 18.dp.toPx()
                val dot = Color(0xFFD5DBD3)
                var y = step / 2
                while (y < size.height) {
                    var x = step / 2
                    while (x < size.width) { drawCircle(dot, 1.2.dp.toPx(), Offset(x, y)); x += step }
                    y += step
                }
            }
            .pointerInput(Unit) { detectTapGestures { select(null) } }
            .then(if (editable) Modifier.onRightClick(first = false) { boardMenu = it } else Modifier)
            .pointerInput(Unit) { detectDragGestures { c, d -> c.consume(); pan += d } }
            // The wheel moves the receipt up and down on the board.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Scroll) {
                            val dy = event.changes.sumOf { it.scrollDelta.y.toDouble() }.toFloat()
                            pan = Offset(pan.x, pan.y - dy * 60f)
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
    ) {
        com.saporini.mobile_desktop.core.money.RealMoney {
            // The paper grows as long as it needs, past the board's edge; the board moves and zooms to show it.
            Box(Modifier.fillMaxWidth().wrapContentHeight(align = Alignment.Top, unbounded = true).padding(top = 112.dp, bottom = 32.dp)
                .onSizeChanged { paperHeight = it.height }.graphicsLayer {
                scaleX = zoom; scaleY = zoom
                translationX = pan.x; translationY = pan.y
                transformOrigin = TransformOrigin(0.5f, 0f)
            }, Alignment.TopCenter) {
                ReceiptPaper(look, facts, picked, editable, select, change)
            }
        }
        Row(Modifier.align(Alignment.TopEnd).padding(10.dp).shadow(3.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp))
            .background(Color.White).border(1.dp, Kit.Border, RoundedCornerShape(10.dp)), verticalAlignment = Alignment.CenterVertically) {
            CanvasTool(Icons.Outlined.Remove, "Smaller", zoom > 0.5f) { zoom = (zoom - 0.1f).coerceAtLeast(0.5f) }
            Text("${(zoom * 100).roundToInt()}%", Modifier.width(46.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                color = Kit.Ink, textAlign = TextAlign.Center, translate = false)
            CanvasTool(Icons.Outlined.Add, "Bigger", zoom < 1.8f) { zoom = (zoom + 0.1f).coerceAtMost(1.8f) }
            Box(Modifier.width(1.dp).height(20.dp).background(Kit.Border))
            Text("Fit", Modifier.clip(RoundedCornerShape(8.dp)).clickable { fit() }.padding(horizontal = 12.dp, vertical = 8.dp),
                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Green)
        }
        // Which receipt is shown (top left): the preliminary bill or the fiscal invoice (with its e-CF type).
        Column(Modifier.align(Alignment.TopStart).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.shadow(3.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).background(Color.White)
                .border(1.dp, Kit.Border, RoundedCornerShape(10.dp)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                ReceiptKind.entries.forEach { kind ->
                    val on = facts.receipt == kind
                    Text(kind.label, Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) Kit.Green else Color.Transparent).clickable { onReceipt(kind) }
                        .padding(horizontal = 12.dp, vertical = 7.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp,
                        color = if (on) Color.White else Kit.Ink, maxLines = 1)
                }
            }
            if (facts.receipt == ReceiptKind.FISCAL) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FiscalType.entries.forEach { type -> ToolChip(type.label, facts.fiscalType == type) { onFiscalType(type) } }
                }
                Row(Modifier.clip(RoundedCornerShape(50)).background(Kit.Amber.copy(alpha = 0.1f)).border(1.dp, Kit.Amber.copy(alpha = 0.3f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Info, null, Modifier.size(14.dp), tint = Kit.Amber)
                    Text("DGII isn't connected yet: test numbers", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = Kit.Amber)
                }
            } else Text("No fiscal number · can be printed again · the order can still change", Modifier.clip(RoundedCornerShape(50)).background(Color.White)
                .border(1.dp, Kit.Border, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp), fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Muted)
        }
        boardMenu?.let { at ->
            Box(Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }.size(1.dp)) { BlockMenu(null) { boardMenu = null } }
        }
        if (editable) Text("Click to pick · drag to move · right-click to copy or paste · the wheel looks around", Modifier.align(Alignment.BottomStart).padding(12.dp),
            fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Faint)
    }
}

@Composable
private fun CanvasTool(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clickable(enabled = enabled, onClick = onClick), Alignment.Center) {
        Icon(icon, label, Modifier.size(17.dp), tint = if (enabled) Kit.Ink else Kit.Faint)
    }
}

/** Till paper: torn top and bottom edges. */
private class TornPaper(private val tooth: Float) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                               density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
        val t = with(density) { tooth.dp.toPx() }
        val teeth = (size.width / (t * 2)).toInt().coerceAtLeast(1)
        val w = size.width / teeth
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, t)
            for (i in 0 until teeth) { lineTo(i * w + w / 2, 0f); lineTo((i + 1) * w, t) }
            lineTo(size.width, size.height - t)
            for (i in teeth downTo 1) { lineTo(i * w - w / 2, size.height); lineTo((i - 1) * w, size.height - t) }
            close()
        }
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}

/** The paper: pictures behind (dragged anywhere), then the blocks one under the other (each dragged up or down). */
@Composable
private fun ReceiptPaper(look: ReceiptDesign, facts: ReceiptFacts, picked: Picked?, editable: Boolean, onPicked: (Picked?) -> Unit,
                         change: ((ReceiptDesign) -> ReceiptDesign) -> Unit) {
    val paper = TornPaper(5f)
    var paperSize by remember { mutableStateOf(IntSize.Zero) }
    val heights = remember { HashMap<String, Int>() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    Box(Modifier.width(PaperWidth).shadow(8.dp, paper).clip(paper).background(PaperColor).onSizeChanged { paperSize = it }) {
        look.backdrops.forEach { b ->
            key(b.id) {
                val wPx = paperSize.width * b.width
                val hPx = wPx / b.aspect.coerceAtLeast(0.05f)
                val chosen = picked == Picked.Behind(b.id)
                RemoteImage(b.imageUrl, null, Modifier.offset { IntOffset((paperSize.width * b.x - wPx / 2).roundToInt(), (paperSize.height * b.y - hPx / 2).roundToInt()) }
                    .size(with(density) { wPx.toDp() }, with(density) { hPx.toDp() }).alpha(b.opacity), contentScale = ContentScale.FillBounds) {}
                // While chosen, a frame on top of everything to drag it anywhere.
                if (chosen && editable) Box(
                    Modifier.zIndex(5f).offset { IntOffset((paperSize.width * b.x - wPx / 2).roundToInt(), (paperSize.height * b.y - hPx / 2).roundToInt()) }
                        .size(with(density) { wPx.toDp() }, with(density) { hPx.toDp() }).dashedFrame(Kit.Green)
                        .pointerInput(b.id) {
                            detectDragGestures { c, d ->
                                c.consume()
                                change { l -> l.copy(backdrops = l.backdrops.map {
                                    if (it.id != b.id || paperSize.width == 0) it
                                    else it.copy(x = (it.x + d.x / paperSize.width).coerceIn(0f, 1f), y = (it.y + d.y / paperSize.height).coerceIn(0f, 1f))
                                }) }
                            }
                        }
                ) {
                    Icon(Icons.Outlined.DragIndicator, null, Modifier.align(Alignment.Center).size(22.dp).clip(RoundedCornerShape(6.dp)).background(Color.White), tint = Kit.Green)
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (look.blocks.isEmpty()) Text("Add something from the left.", Modifier.fillMaxWidth().padding(vertical = 40.dp), fontFamily = Inter(), fontSize = 12.sp,
                color = Kit.Faint, textAlign = TextAlign.Center)
            look.blocks.forEach { block ->
                if (block.visible) key(block.id) {
                    val chosen = picked == Picked.Block(block.id)
                    val interaction = remember { MutableInteractionSource() }
                    val hovered by interaction.collectIsHoveredAsState()
                    var menuAt by remember { mutableStateOf<Offset?>(null) }
                    Box(
                        Modifier.fillMaxWidth().zIndex(if (dragging == block.id) 2f else 0f)
                            .then(if (editable) Modifier.onRightClick(first = true) { onPicked(Picked.Block(block.id)); menuAt = it } else Modifier)
                            .graphicsLayer { translationY = if (dragging == block.id) dragOffset else 0f; shadowElevation = if (dragging == block.id) 6f else 0f }
                            .onSizeChanged { heights[block.id] = it.height }
                            .then(if (editable) Modifier.hoverable(interaction) else Modifier)
                            .then(when {
                                chosen -> Modifier.dashedFrame(Kit.Green).background(Kit.Green.copy(alpha = 0.05f))
                                hovered && editable -> Modifier.dashedFrame(Kit.Green.copy(alpha = 0.45f))
                                else -> Modifier
                            })
                            .then(if (!editable) Modifier else Modifier
                                .pointerInput(block.id) { detectTapGestures { onPicked(Picked.Block(block.id)) } }
                                .pointerInput(block.id) {
                                    detectDragGestures(
                                        onDragStart = { onPicked(Picked.Block(block.id)); dragging = block.id; dragOffset = 0f },
                                        onDragEnd = { dragging = null; dragOffset = 0f },
                                        onDragCancel = { dragging = null; dragOffset = 0f }
                                    ) { c, d ->
                                        c.consume()
                                        dragOffset += d.y
                                        // Past half of the next (or previous) block, it takes that place.
                                        change { l ->
                                            val i = l.blocks.indexOfFirst { it.id == block.id }
                                            // A switched-off neighbour takes no room, so it's passed at once.
                                            fun height(b: ReceiptBlock) = if (!b.visible) 0 else heights[b.id] ?: Int.MAX_VALUE
                                            when {
                                                i < 0 -> l
                                                dragOffset > 0 && i < l.blocks.lastIndex && dragOffset > height(l.blocks[i + 1]) / 2f -> {
                                                    dragOffset -= height(l.blocks[i + 1]).coerceAtMost(100_000)
                                                    l.copy(blocks = l.blocks.toMutableList().apply { add(i + 1, removeAt(i)) })
                                                }
                                                dragOffset < 0 && i > 0 && -dragOffset > height(l.blocks[i - 1]) / 2f -> {
                                                    dragOffset += height(l.blocks[i - 1]).coerceAtMost(100_000)
                                                    l.copy(blocks = l.blocks.toMutableList().apply { add(i - 1, removeAt(i)) })
                                                }
                                                else -> l
                                            }
                                        }
                                    }
                                })
                            .padding(horizontal = 4.dp, vertical = 3.dp)
                    ) {
                        BlockView(block, facts)
                        menuAt?.let { at ->
                            Box(Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }.size(1.dp)) { BlockMenu(block) { menuAt = null } }
                        }
                    }
                }
            }
        }
    }
}

/** A dashed, rounded frame drawn over a block or a picture. */
private fun Modifier.dashedFrame(color: Color): Modifier = drawWithContent {
    drawContent()
    drawRoundRect(color, cornerRadius = CornerRadius(6.dp.toPx()),
        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
}

private fun BlockAlign.horizontal(): Alignment.Horizontal = when (this) {
    BlockAlign.LEFT -> Alignment.Start
    BlockAlign.CENTER -> Alignment.CenterHorizontally
    BlockAlign.RIGHT -> Alignment.End
}

private fun BlockAlign.text(): TextAlign = when (this) {
    BlockAlign.LEFT -> TextAlign.Start
    BlockAlign.CENTER -> TextAlign.Center
    BlockAlign.RIGHT -> TextAlign.End
}

/** One block as the till prints it, with sample figures for the built-in ones. */
@Composable
private fun BlockView(block: ReceiptBlock, facts: ReceiptFacts) {
    val size = block.size.sp.sp
    val weight = if (block.bold) FontWeight.Bold else FontWeight.Normal
    val line: @Composable (String, Boolean) -> Unit = { text, faint ->
        Text(text, Modifier.fillMaxWidth(), fontFamily = FontFamily.Monospace, fontWeight = weight, fontSize = size, lineHeight = (block.size.sp + 4).sp,
            color = if (faint) Kit.Faint else PrintInk, textAlign = block.align.text(), translate = false)
    }
    val row: @Composable (String, String) -> Unit = { name, amount -> ReceiptRow(name, amount, size, weight) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = block.align.horizontal()) {
        when (block.kind) {
            BlockKind.TEXT -> line(block.text.ifBlank { "Your text" }, block.text.isBlank())
            BlockKind.THANKS -> line(block.text.ifBlank { "Thank you" }, false)
            BlockKind.TOP_TEXT -> line(facts.topText.ifBlank { "(top text: write it on the left)" }, facts.topText.isBlank())
            BlockKind.BOTTOM_TEXT -> line(facts.bottomText.ifBlank { "(bottom text: write it on the left)" }, facts.bottomText.isBlank())
            BlockKind.LINE -> ReceiptRule(block.line, Modifier.fillMaxWidth().padding(vertical = 4.dp))
            BlockKind.SPACE -> Spacer(Modifier.height(block.space.dp))
            BlockKind.PICTURE -> block.imageUrl?.let { url ->
                RemoteImage(url, null, Modifier.fillMaxWidth(block.width).height(with(LocalDensity.current) {
                    ((PaperWidth.toPx() - 40.dp.toPx()) * block.width / block.aspect.coerceAtLeast(0.05f)).toDp()
                }).alpha(block.opacity), contentScale = ContentScale.Fit) {}
            } ?: line("(choose a picture)", true)
            BlockKind.BARCODE -> {
                val code = if (block.ownCode) block.code.trim() else "R1042"
                if (code.isEmpty()) line("(write the code on the left)", true)
                else if (com.saporini.mobile_desktop.core.ui.barcodeBars(code) == null) line("(this code can't be a barcode)", true)
                else {
                    Barcode(code, Modifier.fillMaxWidth(block.width).height(38.dp), PrintInk)
                    Text(code, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = PrintInk, translate = false)
                }
            }
            BlockKind.QR -> {
                val link = qrLinkOf(block, facts)
                if (link.isBlank()) line("(write the link on the left)", true)
                else {
                    QrCode(link, PaperWidth * block.width, PrintInk)
                    if (block.text.isNotBlank()) Text(block.text, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, color = PrintInk, translate = false)
                }
            }
            BlockKind.LOGO -> facts.logoUrl?.let { RemoteImage(it, null, Modifier.size(PaperWidth * block.width), contentScale = ContentScale.Fit) {} }
                ?: line("(no logo yet)", true)
            BlockKind.RESTAURANT -> {
                val lines = block.text.takeIf { it.isNotBlank() }?.lines() ?: restaurantLines(facts)
                lines.forEachIndexed { i, text ->
                    if (i == 0) Text(text, Modifier.fillMaxWidth(), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                        fontSize = (block.size.sp + 3).sp, textAlign = block.align.text(), color = PrintInk, translate = false)
                    else line(text, false)
                }
                if (block.text.isBlank() && facts.address == null) line("(no address yet)", true)
                if (block.text.isBlank() && facts.vat == null) line("(no VAT number yet)", true)
            }
            BlockKind.ORDER -> {
                val word = { i: Int -> block.labels.getOrNull(i) ?: OrderLabels[i] }
                row("${word(0)} R1042", "23 Nov 21:14")
                row("${word(1)} T07 · 4 ${word(2)}", "${word(3)} Sara")
            }
            BlockKind.ITEMS -> {
                if (block.text.isNotBlank()) Text(block.text, Modifier.fillMaxWidth().padding(bottom = 3.dp), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    fontSize = size, textAlign = block.align.text(), color = PrintInk, translate = false)
                SampleDishes.forEach { dish ->
                row("${dish.quantity} × ${dish.name}", if (block.prices) dish.price else "")
                if (block.options) dish.options?.let { ReceiptRow("   $it", "", (block.size.sp - 1).sp, FontWeight.Normal) }
                if (block.ingredients) Text("   ${dish.ingredients}", Modifier.fillMaxWidth(), fontFamily = FontFamily.Monospace, fontSize = (block.size.sp - 1.5f).sp,
                    lineHeight = (block.size.sp + 2).sp, color = Kit.Muted, translate = false)
                }
            }
            BlockKind.SUBTOTAL -> row(block.text.ifBlank { defaultLabel(block.kind, facts) }, "39.00")
            BlockKind.VAT -> row(block.text.ifBlank { defaultLabel(block.kind, facts) }, "6.50")
            BlockKind.TOTAL -> row(block.text.ifBlank { defaultLabel(block.kind, facts) }, "39.00")
            BlockKind.PRELIM_NOTICE -> Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).border(1.5.dp, PrintInk, RoundedCornerShape(2.dp))
                .padding(vertical = 6.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("PRECUENTA", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = PrintInk, translate = false)
                Text("NO VÁLIDA PARA FINES FISCALES", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = PrintInk,
                    textAlign = TextAlign.Center, translate = false)
                if (block.text.isNotBlank()) Text(block.text, Modifier.padding(top = 3.dp), fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = PrintInk,
                    textAlign = TextAlign.Center, translate = false)
            }
            BlockKind.FISCAL -> {
                val type = facts.fiscalType
                Text(type.title, Modifier.fillMaxWidth(), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = size,
                    textAlign = block.align.text(), color = PrintInk, translate = false)
                row("e-NCF", sampleEncf(type))
                row("RNC", facts.rnc ?: "1-31-00000-0")
                row("Fecha de emisión", "23-11-2026")
                if (type == FiscalType.E31) {
                    row("RNC comprador", "1-31-12345-6")
                    row("Razón social", "Empresa Ejemplo SRL")
                }
            }
            BlockKind.DGII_QR -> {
                val total = "39.00"
                QrCode(dgiiTimbre((facts.rnc ?: "131000000").filter { it.isDigit() }, if (facts.fiscalType == FiscalType.E31) "131123456" else null,
                    sampleEncf(facts.fiscalType), "23-11-2026", total, "23-11-2026 21:14:05", "AB12CD"), 104.dp, PrintInk)
                Text("Código de seguridad: AB12CD", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = PrintInk, translate = false)
                Text("Fecha de firma: 23-11-2026 21:14:05", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = PrintInk, translate = false)
            }
            BlockKind.PAYMENT -> {
                row("Card", "39.00")
                row(block.text.ifBlank { "Change" }, "0.00")
            }
        }
    }
}

/** A test e-NCF for the preview: the type's code and ten digits. */
private fun sampleEncf(type: FiscalType): String = if (type == FiscalType.E31) "E310000000045" else "E320000000123"

private class SampleDish(val quantity: Int, val name: String, val price: String, val options: String?, val ingredients: String)

private val SampleDishes = listOf(
    SampleDish(2, "Margherita", "18.00", "+ extra mozzarella", "tomato, mozzarella, basil"),
    SampleDish(1, "Tagliatelle ragù", "12.50", "no parmesan", "egg pasta, beef ragù, parmesan"),
    SampleDish(2, "Espresso", "3.00", null, "coffee"),
    SampleDish(1, "Tiramisu", "5.50", null, "mascarpone, coffee, cocoa, ladyfingers")
)

/** A separator in the style chosen: dashes, dots, a line, a double line, stars or equals signs. */
@Composable
private fun ReceiptRule(style: ReceiptLineStyle, modifier: Modifier) {
    when (style) {
        ReceiptLineStyle.STARS, ReceiptLineStyle.EQUALS -> Text((if (style == ReceiptLineStyle.STARS) "* " else "=").repeat(60), modifier,
            fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 10.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Clip,
            softWrap = false, textAlign = TextAlign.Center, translate = false)
        else -> Canvas(modifier.height(if (style == ReceiptLineStyle.DOUBLE) 4.dp else 2.dp)) {
            val ink = Color(0xFF9A9C97)
            val stroke = 1.dp.toPx()
            when (style) {
                ReceiptLineStyle.DASHED -> {
                    val dash = 4.dp.toPx()
                    var x = 0f
                    while (x < size.width) { drawLine(ink, Offset(x, size.height / 2), Offset(minOf(x + dash, size.width), size.height / 2), stroke); x += dash * 2 }
                }
                ReceiptLineStyle.DOTTED -> {
                    val step = 5.dp.toPx()
                    var x = step / 2
                    while (x < size.width) { drawCircle(ink, 1.dp.toPx(), Offset(x, size.height / 2)); x += step }
                }
                ReceiptLineStyle.SOLID -> drawLine(ink, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), stroke)
                ReceiptLineStyle.DOUBLE -> {
                    drawLine(ink, Offset(0f, stroke / 2), Offset(size.width, stroke / 2), stroke)
                    drawLine(ink, Offset(0f, size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), stroke)
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun ReceiptRow(name: String, amount: String, size: androidx.compose.ui.unit.TextUnit, weight: FontWeight) {
    Row(Modifier.fillMaxWidth()) {
        Text(name, Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontWeight = weight, fontSize = size, color = PrintInk, translate = false)
        if (amount.isNotEmpty()) Text(amount, fontFamily = FontFamily.Monospace, fontWeight = weight, fontSize = size, color = PrintInk, translate = false)
    }
}
