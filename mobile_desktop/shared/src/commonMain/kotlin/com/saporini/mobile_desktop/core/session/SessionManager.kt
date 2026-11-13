package com.saporini.mobile_desktop.core.session

import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The restaurant (and branch) the Super Admin or a Co-Super-Admin entered from the restaurant list. */
data class ActingRestaurant(
    val restaurantId: String,
    val restaurantName: String,
    val branchId: String? = null,
    val branchName: String? = null,
    val branchCount: Int = 1
)

/** The Super Admin and Co-Super-Admins belong to no restaurant; they work inside the one they enter. */
fun isPlatformAdmin(user: CurrentUserResponse?): Boolean =
    user?.roles.orEmpty().any { it.equals("SUPER_ADMIN", ignoreCase = true) || it.equals("CO_SUPER_ADMIN", ignoreCase = true) }

fun isSuperAdmin(user: CurrentUserResponse?): Boolean = user?.roles.orEmpty().any { it.equals("SUPER_ADMIN", ignoreCase = true) }

class SessionManager {
    private var userLoader: (suspend () -> CurrentUserResponse)? = null
    private var identityRevision = 0L
    private val refreshLock = kotlinx.coroutines.sync.Mutex()

    fun setUserLoader(loader: suspend () -> CurrentUserResponse) { userLoader = loader }

    suspend fun refreshPermissions() {
        val loader = userLoader ?: return
        refreshLock.lock()
        try {
            val before = signedIn.value ?: return
            val revision = identityRevision
            try {
                val fresh = loader()
                if (revision == identityRevision && signedIn.value?.id == before.id && fresh.id == before.id) signIn(fresh)
            } catch (e: com.saporini.mobile_desktop.core.network.ApiException) {
                if (e.status == 401 && revision == identityRevision) signOut()
                throw e
            }
        } finally { refreshLock.unlock() }
    }

    // The person as the server knows them.
    private val signedIn = MutableStateFlow<CurrentUserResponse?>(null)

    private val _actingRestaurant = MutableStateFlow<ActingRestaurant?>(null)
    val actingRestaurant: StateFlow<ActingRestaurant?> = _actingRestaurant.asStateFlow()
    private val _contextError = MutableStateFlow<String?>(null)
    val contextError: StateFlow<String?> = _contextError.asStateFlow()
    fun clearContextError() { _contextError.value = null }

    /** An old request must never eject a newly chosen branch. */
    fun rejectRestaurantContext(restaurantId: String?, branchId: String?, message: String) {
        val current = _actingRestaurant.value ?: return
        if (current.restaurantId != restaurantId || current.branchId != branchId) return
        _contextError.value = message
        leaveRestaurant()
    }

    /**
     * The person as every screen sees them. For a super admin, the restaurant and branch are the ones they entered
     * (none on the restaurant list), so the POS, Admin Hub, KDS, Statistics and Fraud Detection work for that
     * restaurant without knowing about super admins.
     */
    private val _currentUser = MutableStateFlow<CurrentUserResponse?>(null)
    val currentUser: StateFlow<CurrentUserResponse?> = _currentUser.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(false)

    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    fun signIn(user: CurrentUserResponse) {
        val previous = signedIn.value
        if (previous?.id != user.id) identityRevision++
        signedIn.value = user
        // A fresh sign-in as someone else (or without super admin rights) starts outside any restaurant.
        if (previous?.id != user.id || !isPlatformAdmin(user)) _actingRestaurant.value = null
        if (isPlatformAdmin(user) && "RESTAURANTS_READ" !in user.permissions && _actingRestaurant.value != null) {
            _contextError.value = "Your restaurant access changed. Choose a restaurant again when access is restored."
            _actingRestaurant.value = null
        }
        publish()
        _isAuthenticated.value = true
    }

    /** Super admins only: work inside this restaurant and branch from now on. */
    fun enterRestaurant(restaurant: ActingRestaurant) {
        if (!isPlatformAdmin(signedIn.value) || signedIn.value?.isActive != true || "RESTAURANTS_READ" !in signedIn.value!!.permissions) return
        _contextError.value = null
        _actingRestaurant.value = restaurant
        publish()
    }

    /** Super admins only: switch branch inside the entered restaurant. */
    fun switchBranch(branchId: String, branchName: String) {
        val current = _actingRestaurant.value ?: return
        _actingRestaurant.value = current.copy(branchId = branchId, branchName = branchName)
        publish()
    }

    /** Keeps the shown name up to date after it was changed in the Restaurant app. */
    fun renameActingRestaurant(restaurantId: String, name: String) {
        val current = _actingRestaurant.value ?: return
        if (current.restaurantId != restaurantId || current.restaurantName == name) return
        _actingRestaurant.value = current.copy(restaurantName = name)
    }

    /** Back to the restaurant list. */
    fun leaveRestaurant() {
        _actingRestaurant.value = null
        publish()
    }

    fun signOut() {
        identityRevision++
        signedIn.value = null
        _actingRestaurant.value = null
        _contextError.value = null
        _currentUser.value = null
        _isAuthenticated.value = false
        TokenStore.clear()
    }

    private fun publish() {
        val user = signedIn.value
        val acting = _actingRestaurant.value
        _currentUser.value = when {
            user == null -> null
            !isPlatformAdmin(user) -> user
            else -> user.copy(restaurantId = acting?.restaurantId, defaultBranchId = acting?.branchId)
        }
    }
}
