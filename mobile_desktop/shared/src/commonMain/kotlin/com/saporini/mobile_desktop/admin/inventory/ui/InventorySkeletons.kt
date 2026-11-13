package com.saporini.mobile_desktop.admin.inventory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.SkeletonBox
import com.saporini.mobile_desktop.core.components.SkeletonLight
import com.saporini.mobile_desktop.core.components.SkeletonSummaryCard
import com.saporini.mobile_desktop.core.components.SummaryCardRow
import com.saporini.mobile_desktop.core.components.rememberSkeletonAlpha
import com.saporini.mobile_desktop.core.ui.ScreenSize

// Loading shapes for each Inventory tab, laid out like the real tab so nothing jumps when the data arrives; the screen
// cross-fades from these to the content.

@Composable
internal fun InventoryTabSkeleton(view: InventoryView, size: ScreenSize, modifier: Modifier) {
    val alpha = rememberSkeletonAlpha("inventory-${view.name}")
    Box(modifier.alpha(alpha).clipToBounds()) {
        when (view) {
            InventoryView.OVERVIEW -> OverviewSkeleton(size)
            InventoryView.ITEMS -> ListWithSideSkeleton(size, rowHeight = 68.dp, search = true)
            InventoryView.STOCK -> StockSkeleton(size)
            InventoryView.HISTORY, InventoryView.COUNTS -> ListWithSideSkeleton(size, rowHeight = 64.dp, search = false)
        }
    }
}

@Composable
private fun OverviewSkeleton(size: ScreenSize) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SummaryCardRow(size, List(4) { { m: Modifier -> SkeletonSummaryCard(m) } })
        val chart: @Composable (Modifier) -> Unit = { m -> SkeletonPanel(m) { ChartSkeleton(pulse = false) } }
        val ring: @Composable (Modifier) -> Unit = { m -> SkeletonPanel(m) { RingSkeleton() } }
        if (size.isPhone) {
            chart(Modifier.fillMaxWidth().height(330.dp))
            ring(Modifier.fillMaxWidth().height(300.dp))
        } else {
            Row(Modifier.fillMaxWidth().height(330.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                chart(Modifier.weight(1.7f).fillMaxHeight())
                ring(Modifier.weight(1f).fillMaxHeight())
            }
        }
        val list: @Composable (Modifier) -> Unit = { m -> SkeletonPanel(m) { RowsSkeleton(4, pulse = false) } }
        when (size) {
            ScreenSize.DESKTOP -> Row(Modifier.fillMaxWidth().height(310.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(4) { list(Modifier.weight(1f).fillMaxHeight()) }
            }
            ScreenSize.TABLET -> repeat(2) {
                Row(Modifier.fillMaxWidth().height(310.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(2) { list(Modifier.weight(1f).fillMaxHeight()) }
                }
            }
            ScreenSize.PHONE -> list(Modifier.fillMaxWidth().height(310.dp))
        }
    }
}

@Composable
private fun StockSkeleton(size: ScreenSize) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(if (size.isDesktop) 5 else 3) {
                Row(Modifier.weight(1f).height(76.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)
                    .border(1.dp, Kit.Border, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    SkeletonBox(Modifier.size(42.dp), SkeletonLight, RoundedCornerShape(11.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SkeletonBox(Modifier.width(110.dp).height(12.dp))
                        SkeletonBox(Modifier.width(140.dp).height(9.dp), SkeletonLight)
                    }
                }
            }
        }
        SkeletonPanel(Modifier.fillMaxWidth().weight(1f)) {
            SkeletonBox(Modifier.fillMaxWidth().height(44.dp), SkeletonLight, RoundedCornerShape(10.dp))
            PillsSkeleton()
            CardRowsSkeleton(8, 70.dp)
        }
    }
}

/** A list panel (Items, History, Counts) with the side column of filters on a desktop. */
@Composable
private fun ListWithSideSkeleton(size: ScreenSize, rowHeight: Dp, search: Boolean) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        SkeletonPanel(Modifier.weight(1f).fillMaxHeight()) {
            if (search) SkeletonBox(Modifier.fillMaxWidth().height(44.dp), SkeletonLight, RoundedCornerShape(10.dp))
            PillsSkeleton()
            CardRowsSkeleton(9, rowHeight)
        }
        if (size.isDesktop) {
            Column(Modifier.width(330.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SkeletonPanel(Modifier.fillMaxWidth().height(300.dp)) { FilterRowsSkeleton(6) }
                SkeletonPanel(Modifier.fillMaxWidth().weight(1f)) { FilterRowsSkeleton(4) }
            }
        }
    }
}

// ---- Pieces (also used inside the real panels while their own data loads) ----

/** A white panel with a title bar, like OverviewPanel. */
@Composable
private fun SkeletonPanel(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Kit.Border, RoundedCornerShape(12.dp)).padding(12.dp).clipToBounds(),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            SkeletonBox(Modifier.size(18.dp), shape = RoundedCornerShape(5.dp))
            Spacer(Modifier.width(8.dp))
            SkeletonBox(Modifier.width(120.dp).height(14.dp))
        }
        content()
    }
}

/** The in-and-out chart while it loads: the big figure, bars of different heights and the three totals. */
@Composable
internal fun ChartSkeleton(pulse: Boolean = true) {
    val alpha = if (pulse) rememberSkeletonAlpha("inventory-chart") else 1f
    Column(Modifier.fillMaxWidth().alpha(alpha), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SkeletonBox(Modifier.width(180.dp).height(26.dp))
        Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            listOf(0.55f, 0.3f, 0.7f, 0.45f, 0.85f, 0.35f, 0.6f, 0.25f, 0.5f, 0.75f, 0.4f, 0.65f, 0.3f, 0.9f).forEach { fraction ->
                SkeletonBox(Modifier.weight(1f).fillMaxHeight(fraction), SkeletonLight, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) { SkeletonBox(Modifier.weight(1f).height(52.dp), SkeletonLight, RoundedCornerShape(10.dp)) }
        }
    }
}

@Composable
private fun RingSkeleton() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(160.dp).border(20.dp, SkeletonLight, CircleShape))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { SkeletonBox(Modifier.weight(1f).height(66.dp), SkeletonLight, RoundedCornerShape(10.dp)) }
        }
    }
}

/** Tinted rows like the overview's small lists (icon square, two lines, a figure). */
@Composable
internal fun RowsSkeleton(rows: Int, pulse: Boolean = true) {
    val alpha = if (pulse) rememberSkeletonAlpha("inventory-rows") else 1f
    Column(Modifier.fillMaxWidth().alpha(alpha), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(rows) {
            Row(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(10.dp)).background(Kit.Canvas).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                SkeletonBox(Modifier.size(30.dp), SkeletonLight, RoundedCornerShape(8.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonBox(Modifier.fillMaxWidth(0.55f).height(11.dp))
                    SkeletonBox(Modifier.fillMaxWidth(0.75f).height(8.dp), SkeletonLight)
                }
                SkeletonBox(Modifier.width(48.dp).height(12.dp))
            }
        }
    }
}

@Composable
private fun PillsSkeleton() {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(86.dp, 70.dp, 96.dp, 64.dp).forEach { SkeletonBox(Modifier.width(it).height(30.dp), SkeletonLight, RoundedCornerShape(50)) }
    }
}

/** Only a list's lines loading (after a filter changed), inside its panel; the filters around it stay as they are. */
@Composable
internal fun ListLinesSkeleton(rows: Int, rowHeight: Dp, modifier: Modifier) {
    val alpha = rememberSkeletonAlpha("inventory-lines")
    Box(modifier.alpha(alpha).clipToBounds()) { CardRowsSkeleton(rows, rowHeight) }
}

/** Cards like the lists' StripCards: colour strip, picture, two lines, a figure. */
@Composable
private fun CardRowsSkeleton(rows: Int, rowHeight: Dp) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(rows) {
            Row(Modifier.fillMaxWidth().height(rowHeight).clip(RoundedCornerShape(10.dp)).border(1.dp, Kit.RowBorder, RoundedCornerShape(10.dp)),
                verticalAlignment = Alignment.CenterVertically) {
                SkeletonBox(Modifier.width(5.dp).fillMaxHeight(), SkeletonLight, RoundedCornerShape(0.dp))
                Spacer(Modifier.width(14.dp))
                SkeletonBox(Modifier.size(38.dp), SkeletonLight, RoundedCornerShape(9.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    SkeletonBox(Modifier.fillMaxWidth(0.4f).height(12.dp))
                    SkeletonBox(Modifier.fillMaxWidth(0.6f).height(9.dp), SkeletonLight)
                }
                SkeletonBox(Modifier.padding(end = 14.dp).width(70.dp).height(14.dp))
            }
        }
    }
}

@Composable
private fun FilterRowsSkeleton(rows: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(rows) {
            Row(Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, Kit.RowBorder, RoundedCornerShape(8.dp)).padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically) {
                SkeletonBox(Modifier.size(26.dp), SkeletonLight, RoundedCornerShape(8.dp))
                Spacer(Modifier.width(10.dp))
                SkeletonBox(Modifier.weight(1f).height(11.dp))
                Spacer(Modifier.width(30.dp))
                SkeletonBox(Modifier.width(22.dp).height(11.dp), SkeletonLight)
            }
        }
    }
}
