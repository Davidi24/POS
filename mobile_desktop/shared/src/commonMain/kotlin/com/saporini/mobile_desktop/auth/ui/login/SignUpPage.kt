package com.saporini.mobile_desktop.auth.ui.login

import com.saporini.mobile_desktop.core.network.ApiConfig

/**
 * Where "Don't have an account? Click here" on the sign-in screen goes: the web page where a new restaurant fills in its
 * application (user, 2026-11-17: "the form is filled in on the web, not here"). The app's own application screens
 * (`auth/ui/apply`) are no longer opened from anywhere.
 */
internal object SignUpPage {
    // The page on the web (not built yet); change it here once it has its own address.
    fun url(): String = ApiConfig.BASE_URL.trimEnd('/') + "/public/apply"
}
