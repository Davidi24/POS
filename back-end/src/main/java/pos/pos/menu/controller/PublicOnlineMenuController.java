package pos.pos.menu.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.menu.dto.response.OnlineMenuResponse;
import pos.pos.menu.service.OnlineMenuService;

import java.time.LocalDate;
import java.util.UUID;

// The online menu for the website: sections with the dishes customers can order on that date.
@Tag(name = "Public Online Menu")
@RestController
@RequestMapping("/public/restaurants/{restaurantId}/online-menu")
@RequiredArgsConstructor
public class PublicOnlineMenuController {

    private final OnlineMenuService onlineMenuService;

    @GetMapping
    @Operation(summary = "Get the online menu for a date (defaults to today in the restaurant's time zone)")
    public ResponseEntity<OnlineMenuResponse> getOnlineMenu(
            @PathVariable UUID restaurantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(onlineMenuService.getPublic(restaurantId, date));
    }
}
