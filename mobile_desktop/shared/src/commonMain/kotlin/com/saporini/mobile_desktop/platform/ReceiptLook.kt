package com.saporini.mobile_desktop.platform

import androidx.compose.runtime.mutableStateMapOf

// The receipt, made of blocks (user, 2026-11-23: "I can add an image, a line or a text and move it wherever I want; barcodes,
// QR codes; built-in things like the dishes, the subtotal"). Saved on the server as JSON (`/restaurants/{r}/receipt-design`,
// written by [toJson], read by [receiptLookOf]); pictures are uploaded on their own and kept as links. The top and bottom
// texts are the restaurant's receipt texts (its branding).

/** The separator lines on a receipt. */
enum class ReceiptLineStyle(val label: String) {
    DASHED("Dashes"), DOTTED("Dots"), SOLID("Line"), DOUBLE("Double"), STARS("Stars"), EQUALS("Equals")
}

/** What a QR code on the receipt opens. */
enum class ReceiptQr(val label: String) {
    WEBSITE("Website"), INSTAGRAM("Instagram"), FACEBOOK("Facebook"), MAPS("Google Maps"),
    // Any link or text typed for this QR code (user, 2026-11-23: "when I add a QR code, do I add a link?").
    OWN("Your own link")
}

/**
 * The two receipts a till prints (user, 2026-11-23, DGII: the Dominican Republic's tax office): the preliminary bill
 * (precuenta: before the sale is finished, no fiscal number, printed as often as wanted, the order can still change) and the
 * fiscal invoice (factura fiscal: the e-CF with its e-NCF and DGII's QR code, issued once per sale and reprinted the same).
 * DGII isn't connected yet: the numbers shown are test ones.
 */
enum class ReceiptKind(val label: String) { PRELIMINARY("Preliminary bill"), FISCAL("Fiscal invoice") }

/** The e-CF type on a fiscal invoice: E32 for a final consumer, E31 for a business that takes the tax credit. */
enum class FiscalType(val code: String, val label: String, val title: String) {
    E32("E32", "Consumer (E32)", "FACTURA DE CONSUMO ELECTRÓNICA"),
    E31("E31", "Tax credit (E31)", "FACTURA DE CRÉDITO FISCAL ELECTRÓNICA")
}

/** Everything a receipt can hold: things of your own, then the built-in ones the till fills in. */
enum class BlockKind(val label: String, val builtIn: Boolean, val onlyOn: ReceiptKind? = null) {
    TEXT("Text", false), LINE("Line", false), PICTURE("Picture", false), BARCODE("Barcode", false), QR("QR code", false), SPACE("Space", false),
    LOGO("Logo", true), RESTAURANT("Restaurant details", true), TOP_TEXT("Top text", true), ORDER("Order details", true), ITEMS("Dishes", true),
    SUBTOTAL("Subtotal", true), VAT("VAT", true), TOTAL("Total", true), PAYMENT("Payment", true, ReceiptKind.FISCAL), BOTTOM_TEXT("Bottom text", true),
    THANKS("Thank you", true),
    // Required by DGII on their receipt: kept there (they can be moved, not removed), never on the other.
    PRELIM_NOTICE("Not a fiscal receipt", true, ReceiptKind.PRELIMINARY),
    FISCAL("Fiscal details (e-NCF)", true, ReceiptKind.FISCAL),
    DGII_QR("DGII QR code", true, ReceiptKind.FISCAL);

    val required: Boolean get() = this == PRELIM_NOTICE || this == FISCAL || this == DGII_QR

    /** Whether it can be on [receipt] (a fiscal number never goes on a preliminary bill, nor a payment before it's made). */
    fun allowedOn(receipt: ReceiptKind): Boolean = onlyOn == null || onlyOn == receipt
}

enum class TextSize(val label: String, val sp: Float) { SMALL("S", 9.5f), NORMAL("M", 11f), LARGE("L", 14f), HUGE("XL", 18f) }

enum class BlockAlign(val label: String) { LEFT("Left"), CENTER("Centre"), RIGHT("Right") }

/** One block on the receipt; which fields count depends on its [kind]. */
data class ReceiptBlock(
    val id: String,
    val kind: BlockKind,
    val text: String = "",
    val size: TextSize = TextSize.NORMAL,
    val bold: Boolean = false,
    val align: BlockAlign = BlockAlign.CENTER,
    val line: ReceiptLineStyle = ReceiptLineStyle.DASHED,
    // A picture's link (uploaded at once) and its width ÷ height, so its room is known before it loads.
    val imageUrl: String? = null,
    val aspect: Float = 1f,
    // A picture's, a barcode's, a QR code's or the logo's width, as a share of the paper.
    val width: Float = 0.5f,
    val opacity: Float = 1f,
    val qr: ReceiptQr = ReceiptQr.WEBSITE,
    val space: Int = 14,
    // Dishes: what each line shows.
    val prices: Boolean = true,
    val options: Boolean = false,
    val ingredients: Boolean = false,
    // Switched off in Layers: kept, not printed.
    val visible: Boolean = true,
    // A QR code's own link (with [qr] OWN), and a barcode's own code instead of the receipt's number.
    val link: String = "",
    val ownCode: Boolean = false,
    val code: String = "",
    // A built-in block's own words in place of the till's (user, 2026-11-23: "when I pick a built-in one I can still change its
    // text"): Order details' labels in order (receipt, table, guests, waiter); the others use [text].
    val labels: List<String> = emptyList()
)

/** A picture behind everything, put anywhere on the paper ([x], [y]: its centre, 0–1) at a [width] share and an [opacity]. */
data class Backdrop(val id: String, val imageUrl: String, val aspect: Float = 1f, val x: Float = 0.5f, val y: Float = 0.45f, val width: Float = 0.7f,
                    val opacity: Float = 0.15f)

/** One receipt's design: its blocks from top to bottom and its pictures behind. */
data class ReceiptDesign(val blocks: List<ReceiptBlock>, val backdrops: List<Backdrop> = emptyList())

/**
 * Both receipts, each designed on its own (user, 2026-11-23: "I don't like 'Both', so confusing; I can copy a thing and paste it
 * on the other").
 */
data class ReceiptLook(
    val preliminary: ReceiptDesign = ReceiptDesign(defaultReceipt(ReceiptKind.PRELIMINARY)),
    val fiscal: ReceiptDesign = ReceiptDesign(defaultReceipt(ReceiptKind.FISCAL))
) {
    fun of(receipt: ReceiptKind): ReceiptDesign = if (receipt == ReceiptKind.PRELIMINARY) preliminary else fiscal
    fun with(receipt: ReceiptKind, design: ReceiptDesign): ReceiptLook =
        if (receipt == ReceiptKind.PRELIMINARY) copy(preliminary = design) else copy(fiscal = design)
}

const val MAX_RECEIPT_BLOCK_TEXT = 200

/** A new block's id (random, so it never meets one saved before). */
fun newBlockId(): String = "b" + kotlin.random.Random.nextLong(Long.MAX_VALUE).toString(36)

/** A new block of [kind] with what suits it. */
fun newBlock(kind: BlockKind, imageUrl: String? = null, aspect: Float = 1f): ReceiptBlock {
    val id = newBlockId()
    return when (kind) {
        BlockKind.TEXT -> ReceiptBlock(id, kind, text = "Your text")
        BlockKind.PICTURE -> ReceiptBlock(id, kind, imageUrl = imageUrl, aspect = aspect, width = 0.5f)
        BlockKind.BARCODE -> ReceiptBlock(id, kind, width = 0.78f)
        BlockKind.QR -> ReceiptBlock(id, kind, text = "Scan me", width = 0.34f)
        BlockKind.LOGO -> ReceiptBlock(id, kind, width = 0.22f)
        BlockKind.RESTAURANT -> ReceiptBlock(id, kind)
        BlockKind.TOTAL -> ReceiptBlock(id, kind, size = TextSize.LARGE, bold = true)
        BlockKind.THANKS -> ReceiptBlock(id, kind, text = "Thank you, see you soon!")
        else -> ReceiptBlock(id, kind)
    }
}

/** How each receipt starts: what a till usually prints on it, in its usual order. */
fun defaultReceipt(receipt: ReceiptKind): List<ReceiptBlock> = if (receipt == ReceiptKind.PRELIMINARY) listOf(
    newBlock(BlockKind.LOGO), newBlock(BlockKind.RESTAURANT), newBlock(BlockKind.PRELIM_NOTICE), newBlock(BlockKind.LINE), newBlock(BlockKind.ORDER),
    newBlock(BlockKind.LINE), newBlock(BlockKind.ITEMS), newBlock(BlockKind.LINE), newBlock(BlockKind.SUBTOTAL), newBlock(BlockKind.VAT),
    newBlock(BlockKind.TOTAL), newBlock(BlockKind.LINE), newBlock(BlockKind.THANKS)
) else listOf(
    newBlock(BlockKind.LOGO), newBlock(BlockKind.RESTAURANT), newBlock(BlockKind.TOP_TEXT), newBlock(BlockKind.LINE), newBlock(BlockKind.ORDER),
    newBlock(BlockKind.FISCAL), newBlock(BlockKind.LINE), newBlock(BlockKind.ITEMS), newBlock(BlockKind.LINE), newBlock(BlockKind.SUBTOTAL),
    newBlock(BlockKind.VAT), newBlock(BlockKind.TOTAL), newBlock(BlockKind.PAYMENT), newBlock(BlockKind.LINE), newBlock(BlockKind.BOTTOM_TEXT),
    newBlock(BlockKind.DGII_QR), newBlock(BlockKind.BARCODE), newBlock(BlockKind.THANKS)
)

/** A receipt's blocks with the ones DGII requires on it put back if a receipt made before them doesn't have them. */
fun withRequired(receipt: ReceiptKind, blocks: List<ReceiptBlock>): List<ReceiptBlock> {
    var list = blocks.filter { it.kind.allowedOn(receipt) }
    fun ensure(kind: BlockKind, at: (List<ReceiptBlock>) -> Int) {
        if (kind.allowedOn(receipt) && list.none { it.kind == kind }) {
            val i = at(list).coerceIn(0, list.size)
            list = list.take(i) + newBlock(kind) + list.drop(i)
        }
    }
    ensure(BlockKind.PRELIM_NOTICE) { l -> l.indexOfFirst { it.kind == BlockKind.RESTAURANT } + 1 }
    ensure(BlockKind.FISCAL) { l -> l.indexOfFirst { it.kind == BlockKind.ORDER }.let { if (it < 0) l.size else it + 1 } }
    ensure(BlockKind.DGII_QR) { l -> l.indexOfFirst { it.kind == BlockKind.BARCODE }.let { if (it < 0) l.size else it } }
    return list
}

/**
 * DGII's verification link for a fiscal invoice (the "timbre" its QR code opens). FAKE until DGII is connected: the format
 * is DGII's, the numbers are test ones.
 */
fun dgiiTimbre(rncEmisor: String, rncComprador: String?, encf: String, date: String, total: String, signed: String, code: String): String =
    "https://ecf.dgii.gov.do/testecf/ConsultaTimbre?RncEmisor=$rncEmisor" + (rncComprador?.let { "&RncComprador=$it" } ?: "") +
        "&ENCF=$encf&FechaEmision=$date&MontoTotal=$total&FechaFirma=${signed.replace(" ", "%20")}&CodigoSeguridad=$code"

/** Each restaurant's receipt as saved on the server and as it's being changed (the draft), with the saved version. */
object ReceiptLooks {
    private val saved = mutableStateMapOf<String, ReceiptLook>()
    // What's being changed and not saved yet (user, 2026-11-23: "everything I do here needs Save changes").
    private val drafts = mutableStateMapOf<String, ReceiptLook>()
    private val versions = HashMap<String, Long>()
    // The starting receipt per restaurant, made once (outside the state, so reading it never writes during drawing).
    private val starts = HashMap<String, ReceiptLook>()
    private fun savedOf(restaurantId: String): ReceiptLook = saved[restaurantId] ?: starts.getOrPut(restaurantId) { ReceiptLook() }
    fun of(restaurantId: String): ReceiptLook = drafts[restaurantId] ?: savedOf(restaurantId)
    fun set(restaurantId: String, look: ReceiptLook) { drafts[restaurantId] = look }
    fun changed(restaurantId: String?): Boolean = restaurantId != null && drafts[restaurantId]?.let { it != savedOf(restaurantId) } == true
    fun version(restaurantId: String): Long? = versions[restaurantId]
    fun discard(restaurantId: String) { drafts.remove(restaurantId) }

    /** What the server has ([look] null = never saved: the starting receipt). A draft being changed stays. */
    fun loaded(restaurantId: String, look: ReceiptLook?, version: Long) {
        versions[restaurantId] = version
        if (look != null) saved[restaurantId] = look else saved.remove(restaurantId)
    }

    /** Saved: the draft becomes what the server has. */
    fun markSaved(restaurantId: String, look: ReceiptLook, version: Long) {
        saved[restaurantId] = look
        versions[restaurantId] = version
        if (drafts[restaurantId] == look) drafts.remove(restaurantId)
    }
}

// ---- As JSON, for the server ----

@kotlinx.serialization.Serializable
private data class LookJson(val preliminary: DesignJson, val fiscal: DesignJson)

@kotlinx.serialization.Serializable
private data class DesignJson(val blocks: List<BlockJson> = emptyList(), val backdrops: List<BackdropJson> = emptyList())

@kotlinx.serialization.Serializable
private data class BlockJson(
    val id: String, val kind: String, val text: String = "", val size: String = "NORMAL", val bold: Boolean = false, val align: String = "CENTER",
    val line: String = "DASHED", val imageUrl: String? = null, val aspect: Float = 1f, val width: Float = 0.5f, val opacity: Float = 1f,
    val qr: String = "WEBSITE", val space: Int = 14, val prices: Boolean = true, val options: Boolean = false, val ingredients: Boolean = false,
    val visible: Boolean = true, val link: String = "", val ownCode: Boolean = false, val code: String = "", val labels: List<String> = emptyList()
)

@kotlinx.serialization.Serializable
private data class BackdropJson(val id: String, val imageUrl: String, val aspect: Float = 1f, val x: Float = 0.5f, val y: Float = 0.45f,
                                val width: Float = 0.7f, val opacity: Float = 0.15f)

private val ReceiptJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }

private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E = enumValues<E>().firstOrNull { it.name == name } ?: fallback

private fun ReceiptDesign.json() = DesignJson(
    blocks.map { b ->
        BlockJson(b.id, b.kind.name, b.text, b.size.name, b.bold, b.align.name, b.line.name, b.imageUrl, b.aspect, b.width, b.opacity, b.qr.name,
            b.space, b.prices, b.options, b.ingredients, b.visible, b.link, b.ownCode, b.code, b.labels)
    },
    backdrops.map { BackdropJson(it.id, it.imageUrl, it.aspect, it.x, it.y, it.width, it.opacity) }
)

private fun DesignJson.design(receipt: ReceiptKind) = ReceiptDesign(
    withRequired(receipt, blocks.mapNotNull { b ->
        val kind = BlockKind.entries.firstOrNull { it.name == b.kind } ?: return@mapNotNull null
        ReceiptBlock(b.id, kind, b.text, enumOr(b.size, TextSize.NORMAL), b.bold, enumOr(b.align, BlockAlign.CENTER), enumOr(b.line, ReceiptLineStyle.DASHED),
            b.imageUrl, b.aspect.takeIf { it > 0f } ?: 1f, b.width, b.opacity, enumOr(b.qr, ReceiptQr.WEBSITE), b.space, b.prices, b.options, b.ingredients,
            b.visible, b.link, b.ownCode, b.code, b.labels)
    }),
    backdrops.map { Backdrop(it.id, it.imageUrl, it.aspect.takeIf { a -> a > 0f } ?: 1f, it.x, it.y, it.width, it.opacity) }
)

/** Both receipts as the JSON the server keeps. */
fun ReceiptLook.toJson(): kotlinx.serialization.json.JsonElement =
    ReceiptJson.encodeToJsonElement(LookJson.serializer(), LookJson(preliminary.json(), fiscal.json()))

/** Both receipts from the server's JSON (null when there's none or it can't be read: the starting receipt is used). */
fun receiptLookOf(json: kotlinx.serialization.json.JsonElement?): ReceiptLook? = json?.let {
    runCatching { ReceiptJson.decodeFromJsonElement(LookJson.serializer(), it) }.getOrNull()
}?.let { ReceiptLook(it.preliminary.design(ReceiptKind.PRELIMINARY), it.fiscal.design(ReceiptKind.FISCAL)) }

/** The link a QR code opens, from the details ("" when it isn't there yet). */
fun qrLink(qr: ReceiptQr, website: String?, social: SocialLinks, own: String = ""): String {
    fun site(value: String, host: String): String {
        val v = value.trim()
        return when {
            v.isEmpty() -> ""
            v.startsWith("@") -> "https://$host/${v.drop(1)}"
            v.startsWith("http", ignoreCase = true) -> v
            else -> "https://$v"
        }
    }
    return when (qr) {
        ReceiptQr.WEBSITE -> website?.trim().orEmpty()
        ReceiptQr.INSTAGRAM -> site(social.instagram, "instagram.com")
        ReceiptQr.FACEBOOK -> site(social.facebook, "facebook.com")
        ReceiptQr.MAPS -> site(social.maps, "maps.google.com")
        ReceiptQr.OWN -> own.trim()
    }
}
