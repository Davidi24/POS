package com.saporini.mobile_desktop.pos.payment

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.Inter

private val ActiveOlive = Color(0xFF4B522A)
private val Ink = Color(0xFF202124)
private val Muted = Color(0xFF6E716C)
private val Border = Color(0xFFE7E1DC)
private val TotalOrange = Color(0xFFAD3F08)

@Composable
fun PaymentScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    var selectedMethod by remember { mutableStateOf("Card") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 38.dp, vertical = 28.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("<", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 26.sp, color = Ink)
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Collect Payment",
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                letterSpacing = 0.sp,
                color = Ink
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Choose a payment method to complete the order.",
            fontFamily = Inter(),
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            letterSpacing = 0.sp,
            color = Muted
        )

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(34.dp),
            verticalAlignment = Alignment.Top
        ) {
            PaymentOrderSummary(
                onBack = onBack,
                modifier = Modifier
                    .width(500.dp)
                    .fillMaxHeight()
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                AmountCard()
                PaymentMethodCard(
                    selectedMethod = selectedMethod,
                    onSelected = { selectedMethod = it }
                )
            }
        }
    }
}

@Composable
private fun PaymentOrderSummary(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Order #ORD-1042",
                modifier = Modifier.weight(1f),
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                letterSpacing = 0.sp,
                color = Ink
            )
            StatusChip("Dining In")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(ActiveOlive),
                contentAlignment = Alignment.Center
            ) {
                Text("A6", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Color.White)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Laura Bianchi", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Ink)
                Text("4 guests", fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Muted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("12:45 PM", fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Ink)
                Text("May 24, 2025", fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Ink)
            }
        }

        Divider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Items", modifier = Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Ink)
            Text("4 items", fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Muted)
        }
        paymentItems.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.qty.toString(), modifier = Modifier.width(28.dp), fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Ink)
                Text(item.name, modifier = Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Ink)
                Text(item.price, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Ink)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
                .clip(RoundedCornerShape(9.dp))
                .border(1.dp, Border, RoundedCornerShape(9.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("+  Add Note", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Ink)
                Text("No onions on Alfredo pasta.", fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Muted)
            }
        }

        SummaryRow("Subtotal", "$60.00")
        SummaryRow("Tax (8.5%)", "$5.10")
        SummaryRow("Service Charge (10%)", "$6.40")
        Divider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Total", modifier = Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = Ink)
            Text("$71.50", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 26.sp, color = TotalOrange)
        }

        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFFC9C4BE), RoundedCornerShape(8.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Text("<   Back to Order", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Ink)
        }
    }
}

@Composable
private fun AmountCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(102.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Amount to Collect", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = Ink)
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(15.dp), tint = Muted)
                Spacer(Modifier.width(8.dp))
                Text("All transactions are secure and encrypted.", fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Ink)
            }
        }
        Text("$71.50", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 31.sp, color = TotalOrange)
    }
}

@Composable
private fun PaymentMethodCard(
    selectedMethod: String,
    onSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Select Payment Method", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Ink)
        val methods = listOf(
            PaymentMethod("Card", "Visa, Mastercard, Amex", Icons.Outlined.CreditCard),
            PaymentMethod("Apple Pay", "", null),
            PaymentMethod("Google Pay", "", null),
            PaymentMethod("Contactless", "Tap to pay", null),
            PaymentMethod("Cash", "", Icons.Filled.Payments),
            PaymentMethod("Gift Card", "", null),
            PaymentMethod("Tab / Account", "Pay on account", null),
            PaymentMethod("Split Payment", "Multiple methods", null),
            PaymentMethod("Other", "More options", null)
        )
        methods.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { method ->
                    PaymentMethodTile(
                        method = method,
                        selected = selectedMethod == method.title,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelected(method.title) }
                    )
                }
            }
        }

        PayWithCardBox()

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SmallPaymentAction("Send Payment Link", "Send a secure payment link via SMS or Email", Icons.Outlined.Link, Modifier.weight(1f))
            SmallPaymentAction("Manual Card Entry", "Enter card details manually", Icons.Outlined.CreditCard, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PaymentMethodTile(
    method: PaymentMethod,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(102.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .border(if (selected) 2.dp else 1.dp, if (selected) ActiveOlive else Border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (selected) {
                Text("✓", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = ActiveOlive)
            }
            method.icon?.let {
                Icon(it, contentDescription = null, modifier = Modifier.size(29.dp), tint = ActiveOlive)
            } ?: Text(
                text = method.title.take(2).uppercase(),
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 25.sp,
                color = if (method.title == "Google Pay") Color(0xFF4285F4) else Ink
            )
            Text(method.title, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Ink, textAlign = TextAlign.Center)
            if (method.subtitle.isNotBlank()) {
                Text(method.subtitle, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Muted, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun PayWithCardBox() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CreditCard, contentDescription = null, modifier = Modifier.size(22.dp), tint = Ink)
            Spacer(Modifier.width(12.dp))
            Text("Pay with Card", modifier = Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = Ink)
            CardBrand("VISA", Color(0xFF2750A4))
            CardBrand("MC", Color(0xFFE13D2F))
            CardBrand("AMEX", Color(0xFF1F7EC8))
            CardBrand("DISC", Color(0xFF565656))
            Text(")))", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = Ink)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTerminal()
            Spacer(Modifier.width(34.dp))
            Column {
                Text("Tap, insert or swipe card", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Ink)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Hold card or device near the reader, insert the chip, or swipe the magnetic stripe.",
                    fontFamily = Inter(),
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = Ink
                )
            }
        }
    }
}

@Composable
private fun CardTerminal() {
    Column(
        modifier = Modifier
            .width(92.dp)
            .height(132.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF222222))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color(0xFF111111)),
            contentAlignment = Alignment.Center
        ) {
            Text(")))", color = Color.White, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(10.dp))
        repeat(3) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) {
                    Box(Modifier.size(12.dp).clip(RoundedCornerShape(50)).background(Color(0xFF6D6D6D)))
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(width = 18.dp, height = 8.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFE33B31)))
            Box(Modifier.size(width = 18.dp, height = 8.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFF6C144)))
            Box(Modifier.size(width = 18.dp, height = 8.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF23A55A)))
        }
    }
}

@Composable
private fun SmallPaymentAction(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(76.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = ActiveOlive)
        Spacer(Modifier.width(18.dp))
        Column {
            Text(title, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Ink)
            Text(subtitle, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Muted)
        }
    }
}

@Composable
private fun StatusChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFE8F2DD))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = ActiveOlive)
    }
}

@Composable
private fun CardBrand(text: String, color: Color) {
    Box(
        modifier = Modifier
            .padding(end = 6.dp)
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = color)
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Ink)
        Text(value, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Ink)
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Border))
}

private data class PaymentLineItem(
    val qty: Int,
    val name: String,
    val price: String
)

private data class PaymentMethod(
    val title: String,
    val subtitle: String,
    val icon: ImageVector?
)

private val paymentItems = listOf(
    PaymentLineItem(1, "Margherita Pizza", "$18.00"),
    PaymentLineItem(2, "Fettuccine Alfredo", "$22.00"),
    PaymentLineItem(1, "Insalata Caprese", "$12.00"),
    PaymentLineItem(1, "Tiramisu", "$8.00")
)
