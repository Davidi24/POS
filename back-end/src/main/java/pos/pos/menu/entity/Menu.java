package pos.pos.menu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.user.entity.User;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(
        name = "menus",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_menus_restaurant_code", columnNames = {"restaurant_id", "code"})
        },
        indexes = {
                @Index(name = "idx_menus_restaurant_id", columnList = "restaurant_id"),
                @Index(name = "idx_menus_created_by", columnList = "created_by"),
                @Index(name = "idx_menus_updated_by", columnList = "updated_by")
        }
)
public class Menu extends BaseAuditEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @OneToMany(mappedBy = "menu")
    private List<MenuSection> sections = new ArrayList<>();
}