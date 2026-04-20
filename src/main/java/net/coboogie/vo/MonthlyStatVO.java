package net.coboogie.vo;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "monthly_stats", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "record_month"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyStatVO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserVO user;

    @Column(name = "record_month", nullable = false, length = 7)
    private String recordMonth;

    @Column(name = "diary_count")
    private Integer diaryCount;

    @Column(name = "total_chars")
    private Integer totalChars;

    @Column(name = "emotion_distribution", columnDefinition = "JSON")
    private String emotionDistribution;

    @Column(name = "keyword_cloud", columnDefinition = "JSON")
    private String keywordCloud;

    @Column(name = "top_people", columnDefinition = "JSON")
    private String topPeople;

    @Column(name = "daily_pattern", columnDefinition = "JSON")
    private String dailyPattern;

    @CreationTimestamp
    @Column(name = "calculated_at", nullable = false, updatable = false)
    private LocalDateTime calculatedAt;
}
