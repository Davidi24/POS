package com.saporini.mobile_desktop.platform

import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.session.isPlatformAdmin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Match the API's authorities: a platform role never overrides a denied permission. */
data class RestaurantAccess(val read: Boolean, val edit: Boolean, val manage: Boolean, val create: Boolean, val delete: Boolean)

fun restaurantAccess(user: CurrentUserResponse?): RestaurantAccess {
    val permissions = if (user?.isActive == true) user.permissions else emptyList()
    val platform = isPlatformAdmin(user)
    return RestaurantAccess(
        read = "RESTAURANTS_READ" in permissions,
        edit = "RESTAURANTS_UPDATE" in permissions,
        manage = platform && "RESTAURANTS_UPDATE" in permissions,
        create = platform && "RESTAURANTS_CREATE" in permissions,
        delete = platform && "RESTAURANTS_DELETE" in permissions
    )
}

/** Recheck immediately before a request; an old dialog cannot grant permission. */
internal suspend fun SessionManager.requireRestaurantAccess(allowed: (RestaurantAccess) -> Boolean) {
    val original = currentUser.value
    refreshPermissions()
    val fresh = currentUser.value
    if (original?.id != fresh?.id || original?.restaurantId != fresh?.restaurantId || original?.defaultBranchId != fresh?.defaultBranchId) {
        throw CancellationException("Restaurant session changed")
    }
    if (currentUser.value == null) throw ApiException(401, "Sign in again to continue.")
    if (!allowed(restaurantAccess(currentUser.value))) throw ApiException(403, "You no longer have permission for this action.")
}

/** Update open screens even when the user is sitting in a dialog. The API still checks every request. */
internal fun CoroutineScope.watchRestaurantPermissions(session: SessionManager, active: () -> Boolean) = launch {
    while (isActive) {
        delay(15_000)
        if (active()) {
            try { session.refreshPermissions() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Keep the current screen during a connection failure; writes recheck. */ }
        }
    }
}
