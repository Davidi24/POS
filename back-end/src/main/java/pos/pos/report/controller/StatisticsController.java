package pos.pos.report.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.report.dto.StatisticsDtos.Overview;
import pos.pos.report.dto.StatisticsDtos.ReportInfo;
import pos.pos.report.dto.StatisticsDtos.Sales;
import pos.pos.report.dto.StatisticsDtos.Staff;
import pos.pos.report.service.StatisticsService;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The Statistics workspace: restaurant-local days {@code from}..{@code to} (inclusive), optionally one branch. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/restaurants/{restaurantId}/statistics")
@Tag(name = "Statistics", description = "Sales, staff and payment statistics and downloadable reports")
public class StatisticsController {

    private final StatisticsService statisticsService;

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('REPORTS_READ')")
    @Operation(summary = "Key figures with the period before, sales per day, payment methods, order types, best dishes")
    public ResponseEntity<Overview> overview(
            @PathVariable UUID restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID branchId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(statisticsService.overview(authentication, restaurantId, branchId, from, to));
    }

    @GetMapping("/sales")
    @PreAuthorize("hasAuthority('REPORTS_READ')")
    @Operation(summary = "Sales per day, hour and weekday; by order type, source, floor and menu section; best and slowest dishes")
    public ResponseEntity<Sales> sales(
            @PathVariable UUID restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID branchId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(statisticsService.sales(authentication, restaurantId, branchId, from, to));
    }

    @GetMapping("/staff")
    @PreAuthorize("hasAuthority('REPORTS_READ')")
    @Operation(summary = "Per person: orders, sales, tips, discounts, removals, refunds and hours")
    public ResponseEntity<Staff> staff(
            @PathVariable UUID restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID branchId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(statisticsService.staff(authentication, restaurantId, branchId, from, to));
    }

    @GetMapping("/reports")
    @PreAuthorize("hasAuthority('REPORTS_READ')")
    @Operation(summary = "The reports that can be downloaded")
    public ResponseEntity<List<ReportInfo>> reports(@PathVariable UUID restaurantId, Authentication authentication) {
        return ResponseEntity.ok(statisticsService.reports(authentication, restaurantId));
    }

    @GetMapping(value = "/reports/{code}.csv", produces = "text/csv")
    @PreAuthorize("hasAuthority('REPORTS_READ')")
    @Operation(summary = "Download a report as CSV (daily-sales, items, staff, payments)")
    public ResponseEntity<byte[]> report(
            @PathVariable UUID restaurantId,
            @PathVariable String code,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID branchId,
            Authentication authentication
    ) {
        String csv = statisticsService.csvReport(authentication, restaurantId, branchId, from, to, code);
        String fileName = code + "-" + from + "-to-" + to + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .body(("﻿" + csv).getBytes(StandardCharsets.UTF_8));
    }
}
