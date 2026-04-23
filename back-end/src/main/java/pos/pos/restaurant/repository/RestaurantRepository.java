package pos.pos.restaurant.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.RestaurantStatus;

import java.util.Optional;
import java.util.UUID;

public interface RestaurantRepository extends JpaRepository<Restaurant, UUID> {

    Optional<Restaurant>  findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByCodeAndDeletedAtIsNull(String code);

    boolean existsByCodeAndIdNotAndDeletedAtIsNull(String code, UUID id);

    boolean existsBySlugAndDeletedAtIsNull(String slug);

    boolean existsBySlugAndIdNotAndDeletedAtIsNull(String slug, UUID id);


    /**
     * ============================================================
     * ADVANCED SEARCH + ACCESS CONTROL + PAGINATION QUERY
     * ============================================================
     * <p>
     * This method performs a dynamic, secure, and paginated search
     * over the Restaurant table.
     * <p>
     * It combines:
     * 1. Soft-delete filtering
     * 2. Optional filters (active, status, owner)
     * 3. Text search across multiple fields
     * 4. Access control (who is allowed to see what)
     * 5. Pagination (via Pageable + countQuery)
     * <p>
     *
     * ------------------------------------------------------------
     * 1. SOFT DELETE HANDLING
     * ------------------------------------------------------------
     * r.deletedAt IS NULL
     * <p>
     * Only restaurants that are NOT soft-deleted are returned.
     * Any record with deletedAt != null is considered deleted.
     * <p>
     *
     * ------------------------------------------------------------
     * 2. OPTIONAL FILTERS (DYNAMIC FILTERING)
     * ------------------------------------------------------------
     * Pattern used:
     * (:param IS NULL OR condition)
     * <p>
     * This means:
     * - If parameter is NULL → ignore the filter
     * - If parameter has value → apply the filter
     * <p>
     * Filters included:
     * <p>
     * - active:
     *   (:active IS NULL OR r.isActive = :active)
     * <p>
     * - status:
     *   (:status IS NULL OR r.status = :status)
     * <p>
     * - owner:
     *   (:ownerUserId IS NULL OR r.ownerId = :ownerUserId)
     * <p>
     *
     * ------------------------------------------------------------
     * 3. TEXT SEARCH (CASE-INSENSITIVE)
     * ------------------------------------------------------------
     * Searches across multiple fields using LIKE:
     * <p>
     * lower(r.name)
     * lower(r.legalName)
     * lower(r.code)
     * lower(r.slug)
     * lower(r.email)
     * lower(r.phone)
     * <p>
     * Uses:
     * :searchLike  → expected format: "%value%"
     *
     * Example:
     * searchLike = "%pizza%"
     * → matches anything containing "pizza"
     * <p>
     *
     * ------------------------------------------------------------
     * 4. ACCESS CONTROL (CRITICAL SECURITY LOGIC)
     * ------------------------------------------------------------
     * This ensures users only see allowed data.
     * <p>
     * Conditions:
     * <p>
     * (A) Super Admin:
     *     :superAdmin = true
     *     → can see ALL restaurants
     * <p>
     * (B) Scoped to one restaurant:
     *     r.id = :actorRestaurantId
     *     → user sees ONLY that restaurant
     * <p>
     * (C) Owner:
     *     r.ownerId = :actorUserId
     *     → user sees all restaurants they own
     * <p>
     * If NONE of these are true → NO DATA is returned
     * <p>
     *
     * ------------------------------------------------------------
     * 5. PAGINATION
     * ------------------------------------------------------------
     * Return type:
     * Page<Restaurant>
     * <p>
     * Pageable controls:
     * - page number
     * - page size
     * - sorting
     * <p>
     * Only a subset of data is fetched per request.
     * <p>
     *
     * ------------------------------------------------------------
     * 6. COUNT QUERY (VERY IMPORTANT)
     * ------------------------------------------------------------
     * Spring needs TOTAL number of matching rows
     * to build pagination metadata.
     * <p>
     * Main query:
     *   SELECT r → returns data
     * <p>
     * Count query:
     *   SELECT COUNT(r) → returns total number
     * <p>
     * Both queries MUST use the EXACT SAME filters.
     * <p>
     * Example:
     * - total rows = 57
     * - page size = 10
     * → Spring calculates: 6 pages
     * <p>
     *
     * ------------------------------------------------------------
     * 7. WHY THIS IS DONE IN DATABASE (NOT IN JAVA)
     * ------------------------------------------------------------
     * - Uses DB indexes → faster
     * - Avoids loading all data into memory
     * - Enables real pagination (only needed rows fetched)
     * - Accurate total count without loading data
     * <p>
     *
     * ------------------------------------------------------------
     * SUMMARY
     * ------------------------------------------------------------
     * This method is a production-grade query that:
     * - filters dynamically
     * - searches text fields
     * - enforces security rules
     * - supports pagination efficiently
     * <p>
     * It is designed to scale and should NOT be replaced
     * with in-memory filtering in the application layer.
     * <p>
     * ============================================================
     */
    @Query(
            value = """
            SELECT r
            FROM Restaurant r
            WHERE r.deletedAt IS NULL
              AND (:active IS NULL OR r.isActive = :active)
              AND (:status IS NULL OR r.status = :status)
              AND (:ownerUserId IS NULL OR r.ownerId = :ownerUserId)
              AND (
                    :searchLike IS NULL
                    OR lower(r.name) LIKE :searchLike
                    OR lower(r.legalName) LIKE :searchLike
                    OR lower(r.code) LIKE :searchLike
                    OR lower(r.slug) LIKE :searchLike
                    OR lower(r.email) LIKE :searchLike
                    OR lower(r.phone) LIKE :searchLike
              )
              AND (
                    :superAdmin = true
                    OR (:actorRestaurantId IS NOT NULL AND r.id = :actorRestaurantId)
                    OR r.ownerId = :actorUserId
              )
            """,
            countQuery = """
            SELECT COUNT(r)
            FROM Restaurant r
            WHERE r.deletedAt IS NULL
              AND (:active IS NULL OR r.isActive = :active)
              AND (:status IS NULL OR r.status = :status)
              AND (:ownerUserId IS NULL OR r.ownerId = :ownerUserId)
              AND (
                    :searchLike IS NULL
                    OR lower(r.name) LIKE :searchLike
                    OR lower(r.legalName) LIKE :searchLike
                    OR lower(r.code) LIKE :searchLike
                    OR lower(r.slug) LIKE :searchLike
                    OR lower(r.email) LIKE :searchLike
                    OR lower(r.phone) LIKE :searchLike
              )
              AND (
                    :superAdmin = true
                    OR (:actorRestaurantId IS NOT NULL AND r.id = :actorRestaurantId)
                    OR r.ownerId = :actorUserId
              )
            """
    )
    Page<Restaurant> searchVisibleRestaurants(
            Boolean active,
            RestaurantStatus status,
            UUID ownerUserId,
            String searchLike,
            boolean superAdmin,
            UUID actorUserId,
            UUID actorRestaurantId,
            Pageable pageable
    );
}
