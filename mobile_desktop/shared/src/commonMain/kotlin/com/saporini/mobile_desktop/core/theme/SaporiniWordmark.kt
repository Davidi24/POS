package com.saporini.mobile_desktop.core.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SaporiniWordmark(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Saporini",
            style = MaterialTheme.typography.displayLarge,
            color = SaporiniColors.ForestGreen
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "ITALIANO",
            style = MaterialTheme.typography.titleMedium,
            color = SaporiniColors.Gold,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}