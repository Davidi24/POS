package com.saporini.mobile_desktop.admin

import com.saporini.mobile_desktop.core.network.ApiException

/** Rows per page in the Admin Hub lists (the server allows up to 100). */
const val ADMIN_PAGE_SIZE = 40

/** True when the server said the signed-in person may not do this (or is no longer signed in). */
internal fun isDenied(error: Exception): Boolean = error is ApiException && (error.status == 401 || error.status == 403)

/**
 * The message an Admin Hub screen shows for a failed call. A write whose answer never arrived says so, because it may
 * have been saved anyway.
 */
internal fun adminMessage(error: Exception, write: Boolean): String = when {
    error is ApiException && error.status == 401 -> "Sign in again to continue."
    error is ApiException && error.status == 403 -> "You don't have permission to do this."
    error is ApiException && error.status == 404 -> "This no longer exists. It may have been removed on another device."
    error is ApiException && error.status == 413 -> "This is too large to save."
    error is ApiException && error.status in 400..499 -> error.message.ifBlank { "This can't be saved." }
    error is ApiException && write -> "The server had a problem. Refresh to see whether the change was saved."
    error is ApiException -> "The server had a problem. Try again."
    write -> "No answer from the server. Refresh to see whether the change was saved."
    else -> "Could not load. Check the connection and try again."
}

/** True for answers that mean the list on screen is out of date (removed or changed elsewhere). */
internal fun isStale(error: Exception): Boolean = error is ApiException && (error.status == 404 || error.status == 409)
