package com.saporini.mobile_desktop.pos.menu.domain.model

fun String.isUncategorizedSection(): Boolean = trim().equals("Uncategorized", ignoreCase = true)

/** Stable partition: preserve the user's order, keeping the fallback section last. */
fun <T> List<T>.withUncategorizedLast(name: (T) -> String): List<T> =
    sortedBy { name(it).isUncategorizedSection() }
