package com.saporini.mobile_desktop.auth.ui.login

/** The password rules (Profile → Change password); the server checks the same ones (`PasswordRules`). */
internal fun passwordChecks(password: String): List<Pair<String, Boolean>> = listOf(
    "At least 8 characters" to (password.length >= 8),
    "An uppercase letter" to password.any { it.isUpperCase() },
    "A number" to password.any { it.isDigit() },
    "A special character, like ! or #" to password.any { !it.isLetterOrDigit() }
)
