package pos.pos.user.event;

import java.util.UUID;

/**
 * Someone was removed from a restaurant's staff (Users → Remove). Other modules tidy up what was still ahead for them (user,
 * 2026-11-19): their planned shifts that haven't started go; what happened stays.
 */
public record UserRemovedEvent(UUID userId, UUID restaurantId, UUID actorId) { }
