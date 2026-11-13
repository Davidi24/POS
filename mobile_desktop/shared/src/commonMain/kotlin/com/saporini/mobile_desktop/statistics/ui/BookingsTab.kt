package com.saporini.mobile_desktop.statistics.ui

import mobile_desktop.shared.generated.resources.notification_reservation
import mobile_desktop.shared.generated.resources.Res
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.EventSeat
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TableBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.format.weekdayDate
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.statistics.StatsView

// Statistics → Bookings: how many booked, how many came, when they book for, where bookings come from, no-shows and
// cancellations, how full the tables were and the deposits. MOCK until the reservation statistics exist: the figures
// are made up around the real guests (StatsView.bookings).

private fun bookingSourceLabel(key: String) = when (key) {
    "PHONE" -> "Phone"; "WEBSITE" -> "Website"; "IN_PERSON" -> "In person"; "APPS" -> "Booking apps"; "SOCIAL" -> "Social media"; else -> key
}

private fun bookingSourceColor(key: String) = when (key) {
    "PHONE" -> StatsSeries[0]; "WEBSITE" -> StatsSeries[1]; "IN_PERSON" -> StatsSeries[2]; "APPS" -> StatsSeries[3]; "SOCIAL" -> StatsSeries[4]; else -> StatsOther
}

@Composable
internal fun BookingsSection(v: StatsView, size: ScreenSize) {
    val b = v.bookings
    SectionHeading("Bookings", "How many booked and came, when for, where from, no-shows and how full the tables were",
        Res.drawable.notification_reservation, StatsSeries[5])
    TileGrid(listOf(
        { m -> StatTile("Bookings", countText(b.booked), m, v.change(b.booked, b.compareBooked),
            detail = "${countText(b.booked / v.dayCount.coerceAtLeast(1))} a day", icon = Icons.Outlined.EventSeat) },
        { m -> StatTile("Guests booked", countText(b.guests), m, v.change(b.guests, b.compareGuests),
            detail = "${(b.averageGroup * 10).toInt() / 10f} a booking", icon = Icons.Outlined.Groups, tone = StatsSeries[3]) },
        { m -> StatTile("Came as booked", percent1(b.arrivalRate), m, detail = "${countText(b.arrived)} bookings arrived", icon = Icons.Outlined.EventAvailable,
            tone = StatsSeries[1]) },
        { m -> StatTile("No-shows", countText(b.noShows), m, inverted = true, detail = "${percent1(b.noShows.toFloat() / b.booked.coerceAtLeast(1))} of bookings",
            icon = Icons.Outlined.PersonOff, tone = Kit.Grey) },
        { m -> StatTile("Cancelled", countText(b.cancelled), m, inverted = true, detail = "${percent1(b.cancelled.toFloat() / b.booked.coerceAtLeast(1))} of bookings",
            icon = Icons.Outlined.EventBusy, tone = Kit.Grey) },
        { m -> StatTile("Deposits", v.money(b.depositsTaken), m, detail = "${v.money(b.depositsRefunded)} refunded", icon = Icons.Outlined.Savings, tone = StatsSeries[2]) }
    ), desktop = 6)
    PanelLine(size,
        1.6f to { m -> BookingsByDayPanel(v, m) },
        1f to { m -> PanelStack(m, listOf({ OutcomeFunnelPanel(v, it) }, { BookingSourcesPanel(v, it) })) }
    )
    PanelLine(size,
        1f to { m -> BookingHoursPanel(v, m) },
        1f to { m -> DepositsPanel(v, m) }
    )
    OccupancyPanel(v, Modifier.fillMaxWidth())
}

@Composable
private fun BookingsByDayPanel(v: StatsView, modifier: Modifier) {
    val b = v.bookings
    StatsPanel(Icons.Outlined.CalendarMonth, "Bookings by day", modifier, empty = b.booked == 0L) {
        HeroLine(countText(b.booked), "bookings for ${countText(b.guests)} guests", v.change(b.booked, b.compareBooked))
        ColumnChart(b.days.mapIndexed { i, (date, count) ->
            ColumnDatum(dayLabel(date, v.dayCount), count.toFloat(), "$count bookings · ${b.guestsByDay.getOrNull(i)?.second ?: 0} guests")
        }, roomLeft(), height = 220.dp, color = StatsSeries[0], axisFormat = { compactNumber(it) }, peakLabel = { compactNumber(it) })
        val best = b.days.maxByOrNull { it.second }
        if (best != null) FactLine(listOf(
            Triple("Busiest day", weekdayDate(best.first), "${best.second} bookings"),
            Triple("A day on average", "${b.booked / v.dayCount.coerceAtLeast(1)}", "bookings"),
            Triple("Big groups", "${b.bigGroups}", "6 guests or more")
        ), false)
    }
}

@Composable
private fun OutcomeFunnelPanel(v: StatsView, modifier: Modifier) {
    val b = v.bookings
    StatsPanel(Icons.Outlined.FilterAlt, "From booking to table", modifier, empty = b.booked == 0L) {
        Funnel(listOf(
            Part("BOOKED", "Booked", b.booked.toFloat(), StatsSeries[1], countText(b.booked)),
            Part("KEPT", "Not cancelled", (b.booked - b.cancelled).toFloat(), StatsSeries[1], countText(b.booked - b.cancelled)),
            Part("ARRIVED", "Arrived", b.arrived.toFloat(), Kit.Green, countText(b.arrived))
        ))
        SplitBar(listOf(
            Part("ARRIVED", "Arrived", b.arrived.toFloat(), Kit.Green, countText(b.arrived)),
            Part("CANCELLED", "Cancelled", b.cancelled.toFloat(), Kit.Grey, countText(b.cancelled)),
            Part("NO_SHOW", "Didn't come", b.noShows.toFloat(), Kit.Danger, countText(b.noShows))
        ), Modifier.padding(top = 6.dp), barHeight = 10.dp)
    }
}

@Composable
private fun BookingHoursPanel(v: StatsView, modifier: Modifier) {
    val b = v.bookings
    StatsPanel(Icons.Outlined.AccessTime, "Booked for", modifier, empty = b.booked == 0L, note = "time of day") {
        ColumnChart(b.byHour.map { (hour, count) -> ColumnDatum("$hour", count.toFloat(), "$count bookings at $hour:00") }, roomLeft(), height = 190.dp,
            color = StatsSeries[0], axisFormat = { compactNumber(it) }, peakLabel = { compactNumber(it) })
    }
}

@Composable
private fun BookingSourcesPanel(v: StatsView, modifier: Modifier) {
    val b = v.bookings
    StatsPanel(Icons.Outlined.Share, "Where bookings come from", modifier, empty = b.booked == 0L) {
        SplitBar(b.sources.map { Part(it.key, bookingSourceLabel(it.key), it.count.toFloat(), bookingSourceColor(it.key), countText(it.count)) })
    }
}

@Composable
private fun DepositsPanel(v: StatsView, modifier: Modifier) {
    val b = v.bookings
    StatsPanel(Icons.Outlined.Savings, "Deposits", modifier, empty = b.depositsTaken == 0L, emptyText = "Deposits for big groups show here.") {
        HeroLine(v.money(b.depositsTaken), "taken for ${b.bigGroups} big groups", big = false)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FigureRow("Counted towards the bill", v.money(b.depositsTaken - b.depositsRefunded - b.depositsKept), detail = "the group came and ate")
            FigureRow("Kept", v.money(b.depositsKept), detail = "groups that didn't come", valueColor = Kit.Green)
            FigureRow("Refunded", "-" + v.money(b.depositsRefunded), detail = "cancelled in time", valueColor = Kit.Danger)
        }
        Meter(b.depositsRefunded.toFloat() / b.depositsTaken.coerceAtLeast(1), Kit.Danger)
        PanelNote("${percent1(b.depositsRefunded.toFloat() / b.depositsTaken.coerceAtLeast(1))} of deposits were refunded.")
    }
}

/** How full the tables were, lunch and dinner, each weekday. */
@Composable
private fun OccupancyPanel(v: StatsView, modifier: Modifier) {
    val b = v.bookings
    StatsPanel(Icons.Outlined.TableBar, "How full the tables were", modifier, empty = b.booked == 0L, note = "seats taken") {
        val dinner = b.occupancy.maxBy { it.second.second }
        PanelNote("Fullest: ${weekdayName(dinner.first)} dinner, ${percent1(dinner.second.second)} of seats taken")
        HeatMap(listOf("Lunch", "Dinner"), b.occupancy.map { weekdayName(it.first).take(3) },
            listOf(b.occupancy.map { it.second.first }, b.occupancy.map { it.second.second }), cellHeight = 34.dp,
            format = { percent1(it) }, titleOf = { r, c -> "${weekdayName(b.occupancy[c].first)} ${if (r == 0) "lunch" else "dinner"}" })
    }
}
