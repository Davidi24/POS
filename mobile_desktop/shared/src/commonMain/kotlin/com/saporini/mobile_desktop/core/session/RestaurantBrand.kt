package com.saporini.mobile_desktop.core.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The logo of the restaurant the person works in, shown in the app's bars instead of the app's own (user, 2026-11-23: "the
 * logo up should be the same as down"). Set after signing in (`CurrentUserResponse.restaurantLogoUrl`, or the restaurant's
 * branding for super admins inside one) and when the logo is changed in Restaurant info → Logo & receipts.
 */
object RestaurantBrand {
    var restaurantId: String? by mutableStateOf(null)
        private set
    var logoUrl: String? by mutableStateOf(null)
        private set

    fun set(restaurantId: String?, logoUrl: String?) {
        this.restaurantId = restaurantId
        this.logoUrl = logoUrl?.takeIf { it.isNotBlank() && restaurantId != null }
    }

    /** A logo saved or removed on this device: only for the restaurant shown. */
    fun changed(restaurantId: String, logoUrl: String?) {
        if (this.restaurantId == restaurantId) set(restaurantId, logoUrl)
    }
}
