package net.coboogie.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "recommendations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationVO {

    public enum Status {
        ACTIVE,
        REVEALED,
        REPLACED
    }

    public enum GenerationType {
        INITIAL,
        SHUFFLE
    }

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "draw_id")
    private RecommendationDrawVO draw;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserVO user;

    @Column(name = "round_no")
    private Integer roundNo;

    @Column(name = "iab_main_category", nullable = false, length = 50)
    private String iabMainCategory;

    @Column(name = "iab_sub_category", length = 50)
    private String iabSubCategory;

    @Column(name = "content_type", nullable = false, length = 20)
    private String contentType;

    @Column(name = "content_ref", columnDefinition = "JSON")
    private String contentRef;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "card_index")
    private Integer cardIndex;

    @Column(name = "revealed", nullable = false)
    @Builder.Default
    private Boolean revealed = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "generation_type", nullable = false, length = 20)
    @Builder.Default
    private GenerationType generationType = GenerationType.INITIAL;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
