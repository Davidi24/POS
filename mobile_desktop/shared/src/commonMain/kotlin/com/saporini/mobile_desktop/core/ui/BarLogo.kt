package com.saporini.mobile_desktop.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.saporini.mobile_desktop.core.session.RestaurantBrand

/**
 * The logo at the start of a top bar: the restaurant's own, plain (no tile behind it, user 2026-11-23: "why did you add a
 * background?"), in the box the app's logo had ([size]), or [appLogo] when it has none (and outside a restaurant).
 */
@Composable
internal fun BarLogo(size: Dp, appLogo: @Composable () -> Unit) {
    val url = RestaurantBrand.logoUrl
    if (url == null) {
        appLogo()
        return
    }
    Box(Modifier.size(size), Alignment.Center) {
        RemoteImage(url, "Logo", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) {}
    }
}
