package pos.pos.reservation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.reservation.dto.ReservationOccasionDto;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.entity.ReservationOccasion;
import pos.pos.reservation.repository.ReservationOccasionRepository;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// Occasions a booking can have, each with its own icon and options (Admin Hub → Settings → Reservations).
// The starting set is created the first time a restaurant opens them.
@Service
@RequiredArgsConstructor
public class ReservationOccasionService {

    private record Default(String code, String name, String icon, List<String> options) {
    }

    // Agreed with the owner (2026-09-27).
    private static final List<Default> DEFAULTS = List.of(
            new Default("BIRTHDAY", "Birthday", "🎂", List.of("Cake from us", "Guest brings a cake", "Candles", "Birthday song", "Decoration")),
            new Default("ANNIVERSARY", "Anniversary", "❤️", List.of("Flowers", "Prosecco or champagne", "Dessert with a message")),
            new Default("ENGAGEMENT", "Engagement", "💍", List.of("It's a surprise (ring hidden)", "Flowers", "Champagne", "Quiet table")),
            new Default("GRADUATION", "Graduation", "🎓", List.of("Cake", "Decoration", "Champagne")),
            new Default("DATE_NIGHT", "Date night", "🌹", List.of("Quiet or window table", "Candles")),
            new Default("BUSINESS", "Business", "💼", List.of("Quiet table", "Invoice with company details")),
            new Default("OTHER", "Other", "✨", List.of())
    );

    private final RestaurantScopeService restaurantScopeService;
    private final ReservationOccasionRepository reservationOccasionRepository;

    @Transactional
    public List<ReservationOccasionDto> getOccasions(Authentication authentication, UUID restaurantId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return occasionsOf(restaurantId).stream().map(ReservationOccasionService::toDto).toList();
    }

    // Saves the whole list in order; occasions left out are removed (bookings keep what they picked).
    @Transactional
    public List<ReservationOccasionDto> saveOccasions(Authentication authentication, UUID restaurantId, List<ReservationOccasionDto> occasions) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Map<String, ReservationOccasion> existing = occasionsOf(restaurantId).stream()
                .collect(Collectors.toMap(ReservationOccasion::getCode, Function.identity()));
        Set<String> kept = new HashSet<>();
        List<ReservationOccasion> saved = new ArrayList<>();
        int order = 0;
        for (ReservationOccasionDto input : occasions) {
            String code = input.getCode() == null || input.getCode().isBlank() ? codeOf(input.getName()) : input.getCode().trim().toUpperCase();
            if (!kept.add(code)) {
                throw new AuthException("Two occasions can't have the same name", HttpStatus.BAD_REQUEST);
            }
            ReservationOccasion occasion = existing.getOrDefault(code, new ReservationOccasion());
            occasion.setRestaurantId(restaurantId);
            occasion.setCode(code);
            occasion.setName(input.getName().trim());
            occasion.setIcon(input.getIcon().trim());
            occasion.setOptionList(input.getOptions());
            occasion.setActive(input.getActive() == null || input.getActive());
            occasion.setDisplayOrder(order++);
            saved.add(reservationOccasionRepository.save(occasion));
        }
        existing.values().stream().filter(occasion -> !kept.contains(occasion.getCode())).forEach(reservationOccasionRepository::delete);
        reservationOccasionRepository.flush();
        return saved.stream().map(ReservationOccasionService::toDto).toList();
    }

    // Puts the picked occasion on a booking (a copy of its name and icon), or clears it with an empty code.
    public void apply(Reservation reservation, String code, List<String> options, String note) {
        if (code == null || code.isBlank()) {
            reservation.setOccasionCode(null);
            reservation.setOccasionName(null);
            reservation.setOccasionIcon(null);
            reservation.setOccasionOptions(null);
            reservation.setOccasionNote(note == null || note.isBlank() ? null : note.trim());
            return;
        }
        ReservationOccasion occasion = occasionsOf(reservation.getRestaurant().getId()).stream()
                .filter(candidate -> candidate.getCode().equalsIgnoreCase(code.trim()))
                .findFirst()
                .orElseThrow(() -> new AuthException("This occasion isn't available", HttpStatus.BAD_REQUEST));
        List<String> picked = options == null ? List.of() : options.stream()
                .filter(option -> option != null && !option.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        List<String> known = occasion.optionList();
        if (!known.containsAll(picked)) {
            throw new AuthException("Pick options offered for " + occasion.getName(), HttpStatus.BAD_REQUEST);
        }
        reservation.setOccasionCode(occasion.getCode());
        reservation.setOccasionName(occasion.getName());
        reservation.setOccasionIcon(occasion.getIcon());
        reservation.setOccasionOptions(picked.isEmpty() ? null : String.join("\n", picked));
        reservation.setOccasionNote(note == null || note.isBlank() ? null : note.trim());
    }

    // For the guest booking page: the occasions guests can pick.
    @Transactional
    public List<ReservationOccasionDto> activeOccasions(UUID restaurantId) {
        return occasionsOf(restaurantId).stream().filter(ReservationOccasion::isActive).map(ReservationOccasionService::toDto).toList();
    }

    private List<ReservationOccasion> occasionsOf(UUID restaurantId) {
        List<ReservationOccasion> occasions = reservationOccasionRepository.findAllByRestaurantIdOrderByDisplayOrderAscNameAsc(restaurantId);
        if (!occasions.isEmpty()) {
            return occasions;
        }
        List<ReservationOccasion> created = new ArrayList<>();
        int order = 0;
        for (Default preset : DEFAULTS) {
            ReservationOccasion occasion = new ReservationOccasion();
            occasion.setRestaurantId(restaurantId);
            occasion.setCode(preset.code());
            occasion.setName(preset.name());
            occasion.setIcon(preset.icon());
            occasion.setOptionList(preset.options());
            occasion.setDisplayOrder(order++);
            created.add(reservationOccasionRepository.save(occasion));
        }
        return created;
    }

    private static String codeOf(String name) {
        String plain = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String code = plain.toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (code.isEmpty()) {
            code = "OCCASION";
        }
        return code.length() > 40 ? code.substring(0, 40) : code;
    }

    private static ReservationOccasionDto toDto(ReservationOccasion occasion) {
        return ReservationOccasionDto.builder()
                .code(occasion.getCode())
                .name(occasion.getName())
                .icon(occasion.getIcon())
                .options(occasion.optionList())
                .active(occasion.isActive())
                .build();
    }
}
