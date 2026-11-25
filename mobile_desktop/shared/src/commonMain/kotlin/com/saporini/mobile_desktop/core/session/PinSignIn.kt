package com.saporini.mobile_desktop.core.session

import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse

/**
 * The server's mark on a PIN sign-in's permissions (`PinSignIn.MARK`; user, 2026-11-25: "a PIN can do the job, a password can
 * change the system"). The server already leaves out what needs the password, so screens that follow the permissions hide it
 * by themselves; this mark is for what isn't a permission (downloads, your own account, the profile menu's "Use your password").
 */
const val PIN_SIGN_IN = "PIN_SIGN_IN"

fun signedInWithPin(user: CurrentUserResponse?): Boolean = PIN_SIGN_IN in user?.permissions.orEmpty()

/** Once: the next sign-in screen is the password form, even on a till ("Use your password"). Cleared at the next sign-in. */
object PasswordNext {
    var once: Boolean = false
}

/** "Use your password": sign out of the PIN sign-in, and the till shows the password form instead of the PIN pad. */
fun usePasswordInstead(session: SessionManager) {
    PasswordNext.once = true
    session.signOut()
}
