package net.coboogie.vo;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "recommendations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationVO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserVO user;

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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
