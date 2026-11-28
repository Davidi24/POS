package com.saporini.mobile_desktop.pos.menu.ui.item

// What an editor does with a photo when it saves (dishes, stock items). The photo is uploaded after the thing itself is saved.
sealed interface PhotoChange {
    data object Keep : PhotoChange
    data object Remove : PhotoChange

    class Replace(val bytes: ByteArray, val fileName: String, val contentType: String) : PhotoChange
}

// Same limits as the server: PNG, JPEG or WebP, at most 5 MB.
internal const val MaxPhotoBytes = 5 * 1024 * 1024

internal fun photoContentType(fileName: String): String? = when (fileName.substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "webp" -> "image/webp"
    else -> null
}
