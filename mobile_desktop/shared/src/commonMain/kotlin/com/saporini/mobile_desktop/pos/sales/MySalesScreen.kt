package com.saporini.mobile_desktop.pos.sales

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.TableRestaurant
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.Inter
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.auth_login_img
import org.jetbrains.compose.resources.painterResource

private val ActiveOlive = Color(0xFF4B522A)
private val Ink = Color(0xFF202124)
private val Muted = Color(0xFF6F716E)
private val Border = Color(0xFFE7E1DC)
private val SoftGreen = Color(0xFFE5F0D8)
private val SoftGold = Color(0xFFF5E7C4)
private val Orange = Color(0xFFE06412)

@Composable
fun MySalesScreen(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SalesHeader()
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            KpiCard("Total Sales", "$1,284.50", "↗ 12.6% vs yesterday", Icons.Outlined.Payments, SoftGreen, Modifier.weight(1f))
            KpiCard("Orders Served", "28", "↗ 7 vs yesterday", Icons.AutoMirrored.Outlined.ReceiptLong, SoftGold, Modifier.weight(1f))
            KpiCard("Average Ticket", "$45.88", "↗ $3.12 vs yesterday", Icons.Outlined.CreditCard, SoftGreen, Modifier.weight(1f))
            KpiCard("Tips Earned", "$186.40", "↗ 15.3% vs yesterday", Icons.Outlined.Payments, SoftGold, Modifier.weight(1f))
            KpiCard("Tables Served", "14", "↗ 2 vs yesterday", Icons.Outlined.TableRestaurant, SoftGreen, Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SalesChartCard(Modifier.weight(1.4f))
            PaymentMethodsCard(Modifier.weight(0.92f))
            ShiftSummaryCard(Modifier.weight(0.94f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TopSellingItemsCard(Modifier.weight(1f))
            TablesServedCard(Modifier.weight(0.92f))
            RecentPaymentsCard(Modifier.weight(1.15f))
        }

        ShiftFooter()
    }
}

@Composable
private fun SalesHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("My Sales", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 30.sp, color = Ink, letterSpacing = 0.sp)
            Spacer(Modifier.height(4.dp))
            Text("Track your shift revenue, tips, and served orders.", fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Muted)
        }
        HeaderFilter("Today, May 24, 2025", Icons.Outlined.CalendarToday, 230.dp)
        Spacer(Modifier.width(14.dp))
        HeaderFilter("Lunch & Dinner Shift", Icons.Outlined.AccessTime, 235.dp)
        Spacer(Modifier.width(14.dp))
        HeaderPill("Today", false)
        Spacer(Modifier.width(10.dp))
        HeaderPill("Mine", true)
    }
}

@Composable
private fun HeaderFilter(text: String, icon: ImageVector, width: androidx.compose.ui.unit.Dp) {
    Row(
        modifier = Modifier
            .width(width)
            .height(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = Ink)
        Spacer(Modifier.width(10.dp))
        Text(text, modifier = Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Ink)
        Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp), tint = Ink)
    }
}

@Composable
private fun HeaderPill(text: String, selected: Boolean) {
    Box(
        modifier = Modifier
            .width(92.dp)
            .height(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) ActiveOlive else Color.White)
            .border(1.dp, if (selected) ActiveOlive else Border, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (selected) Color.White else Ink)
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    delta: String,
    icon: ImageVector,
    iconBg: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(114.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .background(Color.White)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp), tint = ActiveOlive)
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Ink)
            Spacer(Modifier.height(6.dp))
            Text(value, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = Ink)
            Spacer(Modifier.height(8.dp))
            Text(delta, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Color(0xFF2E8A2A))
        }
    }
}

@Composable
private fun SalesChartCard(modifier: Modifier = Modifier) {
    Panel(modifier.height(292.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PanelTitle("Sales During Shift", Modifier.weight(1f))
            TextButton(
                onClick = {},
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .border(1.dp, Border, RoundedCornerShape(7.dp)),
                shape = RoundedCornerShape(7.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = Ink)
            ) {
                Text("By Hour", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        SalesLineChart()
    }
}

@Composable
private fun SalesLineChart() {
    val values = listOf(45f, 120f, 165f, 210f, 150f, 180f, 230f, 265f, 190f, 145f, 90f, 34f)
    val labels = listOf("11 AM", "12 PM", "1 PM", "2 PM", "3 PM", "4 PM", "5 PM", "6 PM", "7 PM", "8 PM", "9 PM", "10 PM")
    Column {
        Box(Modifier.fillMaxWidth().height(176.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val max = 300f
                val left = 48f
                val right = size.width - 8f
                val top = 8f
                val bottom = size.height - 24f
                repeat(6) { index ->
                    val y = top + (bottom - top) * index / 5f
                    drawLine(Color(0xFFE9E5DF), Offset(left, y), Offset(right, y), strokeWidth = 1f)
                }
                val points = values.mapIndexed { index, value ->
                    val x = left + (right - left) * index / (values.size - 1)
                    val y = bottom - (bottom - top) * (value / max)
                    Offset(x, y)
                }
                val area = Path().apply {
                    moveTo(points.first().x, bottom)
                    points.forEach { lineTo(it.x, it.y) }
                    lineTo(points.last().x, bottom)
                    close()
                }
                drawPath(area, ActiveOlive.copy(alpha = 0.12f))
                for (i in 0 until points.lastIndex) {
                    drawLine(ActiveOlive, points[i], points[i + 1], strokeWidth = 2.5f)
                }
                points.forEach { point -> drawCircle(ActiveOlive, radius = 4.5f, center = point) }
            }
            val yLabels = listOf("$300", "$240", "$180", "$120", "$60", "$0")
            Column(
                modifier = Modifier
                    .height(154.dp)
                    .padding(top = 1.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                yLabels.forEach { Text(it, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 11.sp, color = Muted) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 50.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEach { Text(it, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 10.sp, color = Ink) }
        }
    }
}

@Composable
private fun PaymentMethodsCard(modifier: Modifier = Modifier) {
    Panel(modifier.height(292.dp)) {
        PanelTitle("Payment Methods")
        Spacer(Modifier.height(16.dp))
        paymentMethods.forEach { method ->
            PaymentMethodRow(method)
            Spacer(Modifier.height(13.dp))
        }
        Divider()
        Spacer(Modifier.height(10.dp))
        Row {
            Text("Total", modifier = Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Ink)
            Text("100%", modifier = Modifier.width(62.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Ink)
            Text("$1,284.50", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Ink)
        }
    }
}

@Composable
private fun PaymentMethodRow(method: PaymentMethodSale) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(SoftGreen), contentAlignment = Alignment.Center) {
            Text(method.short, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = ActiveOlive)
        }
        Spacer(Modifier.width(10.dp))
        Text(method.name, modifier = Modifier.width(76.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Ink)
        Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFE4E7DF))) {
            Box(Modifier.fillMaxWidth(method.percent / 60f).height(5.dp).clip(RoundedCornerShape(5.dp)).background(ActiveOlive))
        }
        Spacer(Modifier.width(12.dp))
        Text("${method.percent.toInt()}%", modifier = Modifier.width(38.dp), fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Ink)
        Text(method.amount, modifier = Modifier.width(72.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Ink)
    }
}

@Composable
private fun ShiftSummaryCard(modifier: Modifier = Modifier) {
    Panel(modifier.height(292.dp)) {
        PanelTitle("Shift Summary")
        Spacer(Modifier.height(18.dp))
        SummaryRow("Subtotal", "$1,088.70")
        SummaryRow("Tax (8.5%)", "$92.54")
        SummaryRow("Service Charge (10%)", "$108.87")
        Spacer(Modifier.height(14.dp))
        Divider()
        Spacer(Modifier.height(14.dp))
        SummaryRow("Tips", "$186.40", valueColor = Color(0xFF2E8A2A))
        SummaryRow("Refunds", "-$7.01", valueColor = Color(0xFFD71920))
        Spacer(Modifier.height(14.dp))
        Divider()
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Total Collected", modifier = Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = Ink)
            Text("$1,469.50", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 25.sp, color = Orange)
        }
    }
}

@Composable
private fun TopSellingItemsCard(modifier: Modifier = Modifier) {
    Panel(modifier.height(290.dp)) {
        PanelTitle("Top Selling Items")
        Spacer(Modifier.height(12.dp))
        Row {
            TableHeader("Item", Modifier.weight(1f))
            TableHeader("Qty Sold", Modifier.width(90.dp))
            TableHeader("Sales", Modifier.width(90.dp), Alignment.End)
        }
        topItems.forEach {
            ItemSalesRow(it)
            Divider()
        }
        LinkText("View all items")
    }
}

@Composable
private fun ItemSalesRow(item: TopItem) {
    Row(Modifier.height(34.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(Res.drawable.auth_login_img),
            contentDescription = item.name,
            modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(10.dp))
        Text(item.name, modifier = Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Ink)
        Text(item.qty.toString(), modifier = Modifier.width(90.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Ink)
        Text(item.sales, modifier = Modifier.width(90.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Ink)
    }
}

@Composable
private fun TablesServedCard(modifier: Modifier = Modifier) {
    Panel(modifier.height(290.dp)) {
        PanelTitle("Tables Served")
        Spacer(Modifier.height(12.dp))
        Row {
            TableHeader("Area", Modifier.weight(1f))
            TableHeader("Tables", Modifier.width(130.dp))
            TableHeader("Sales", Modifier.width(90.dp), Alignment.End)
        }
        tableAreas.forEach { area ->
            Row(Modifier.height(34.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(area.name, modifier = Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Ink)
                Box(Modifier.width(130.dp).height(5.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFE4E7DF))) {
                    Box(Modifier.fillMaxWidth(area.progress).height(5.dp).clip(RoundedCornerShape(5.dp)).background(ActiveOlive))
                }
                Text(area.sales, modifier = Modifier.width(90.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Ink)
            }
            Divider()
        }
        LinkText("View all tables")
    }
}

@Composable
private fun RecentPaymentsCard(modifier: Modifier = Modifier) {
    Panel(modifier.height(290.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PanelTitle("Recent Payments", Modifier.weight(1f))
            Box(Modifier.width(74.dp).height(30.dp).clip(RoundedCornerShape(7.dp)).border(1.dp, Border, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                Text("View All", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Ink)
            }
        }
        Spacer(Modifier.height(10.dp))
        recentPayments.forEach { payment ->
            RecentPaymentRow(payment)
            Divider()
        }
    }
}

@Composable
private fun RecentPaymentRow(payment: RecentPayment) {
    Row(Modifier.height(41.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(7.dp)).background(payment.color),
            contentAlignment = Alignment.Center
        ) {
            Text(payment.table, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Table ${payment.table}", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Ink)
            Text(payment.guests, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 11.sp, color = Muted)
        }
        Text(payment.time, modifier = Modifier.width(64.dp), fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Muted)
        Text(payment.method, modifier = Modifier.width(108.dp), fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Ink)
        Column(horizontalAlignment = Alignment.End) {
            Text(payment.amount, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Ink)
            Text("Paid  ✓", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Color(0xFF2E8A2A))
        }
    }
}

@Composable
private fun ShiftFooter() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(ActiveOlive), contentAlignment = Alignment.Center) {
            Text("DK", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.width(140.dp)) {
            Text("Dario K.", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Ink)
            Text("Waiter", fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Muted)
        }
        FooterMetric("Shift Start", "11:00 AM")
        FooterMetric("Current Time", "9:45 PM", Color(0xFF2E8A2A))
        FooterMetric("Shift Duration", "10h 45m")
        FooterMetric("Orders In Progress", "0")
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .width(300.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ActiveOlive)
                .clickable {},
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, modifier = Modifier.size(22.dp), tint = Color.White)
                Text("End Shift", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
            }
        }
    }
}

@Composable
private fun FooterMetric(label: String, value: String, color: Color = Ink) {
    Box(Modifier.width(175.dp).height(52.dp).border(1.dp, Color.Transparent), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Muted)
            Spacer(Modifier.height(6.dp))
            Text(value, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = color)
        }
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .background(Color.White)
            .padding(16.dp),
        content = { content() }
    )
}

@Composable
private fun PanelTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Ink)
}

@Composable
private fun SummaryRow(label: String, value: String, valueColor: Color = Ink) {
    Row(Modifier.height(34.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Ink)
        Text(value, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = valueColor)
    }
}

@Composable
private fun TableHeader(text: String, modifier: Modifier = Modifier, alignment: Alignment.Horizontal = Alignment.Start) {
    Box(modifier.height(24.dp), contentAlignment = when (alignment) {
        Alignment.End -> Alignment.CenterEnd
        else -> Alignment.CenterStart
    }) {
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = ActiveOlive)
    }
}

@Composable
private fun LinkText(text: String) {
    Spacer(Modifier.height(8.dp))
    Text("$text  ›", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF2E8A2A))
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Border))
}

private data class PaymentMethodSale(val short: String, val name: String, val percent: Float, val amount: String)
private data class TopItem(val name: String, val qty: Int, val sales: String)
private data class TableArea(val name: String, val progress: Float, val sales: String)
private data class RecentPayment(val table: String, val guests: String, val time: String, val method: String, val amount: String, val color: Color)

private val paymentMethods = listOf(
    PaymentMethodSale("C", "Card", 54f, "$693.00"),
    PaymentMethodSale("$", "Cash", 21f, "$270.00"),
    PaymentMethodSale("AP", "Apple Pay", 13f, "$166.50"),
    PaymentMethodSale("G", "Google Pay", 7f, "$89.50"),
    PaymentMethodSale("…", "Other", 5f, "$65.50")
)

private val topItems = listOf(
    TopItem("Margherita Pizza", 12, "$216.00"),
    TopItem("Spaghetti Carbonara", 9, "$179.10"),
    TopItem("Tiramisu", 8, "$96.00"),
    TopItem("Bruschetta", 10, "$75.00"),
    TopItem("Limonata", 15, "$60.00")
)

private val tableAreas = listOf(
    TableArea("Main Salon", 0.95f, "$624.30"),
    TableArea("Terrace", 0.55f, "$312.40"),
    TableArea("Round Salon", 0.34f, "$184.50"),
    TableArea("Private Salon", 0.18f, "$113.80"),
    TableArea("Bar", 0.10f, "$49.50")
)

private val recentPayments = listOf(
    RecentPayment("A6", "4 guests", "9:12 PM", "Card", "$78.50", ActiveOlive),
    RecentPayment("R3", "2 guests", "8:45 PM", "Cash", "$29.00", ActiveOlive),
    RecentPayment("T2", "3 guests", "8:15 PM", "Split Payment", "$112.30", Color(0xFFE9B94E)),
    RecentPayment("B1", "2 guests", "7:42 PM", "Apple Pay", "$64.20", Color(0xFFE8D4A8)),
    RecentPayment("A3", "5 guests", "7:05 PM", "Card", "$96.80", ActiveOlive)
)
