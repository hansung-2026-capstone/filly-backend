package net.coboogie.vo;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_diary_analysis")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiEmotionAnalysisVO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diary_id", nullable = false)
    private DiaryEntryVO diary;

    // 레이어 1: 감정 (상위 3~5개, {"name": "기쁨", "score": 0.35})
    @Column(name = "emotions", columnDefinition = "JSON")
    private String emotions;

    @Column(name = "happiness_index", comment="사용자 행복 지수")
    private Integer happinessIndex;

    // 레이어 2: 활동 / 장소 / 사람
    @Column(name = "activities", columnDefinition = "JSON")
    private String activities;

    @Column(name = "places", columnDefinition = "JSON")
    private String places;

    @Column(name = "people", columnDefinition = "JSON")
    private String people;

    // 레이어 3: IAB 취향 태그
    @Column(name = "iab_categories", columnDefinition = "JSON")
    private String iabCategories;

    // 레이어 4: 일상 패턴 (시간/요일/식사/카페인 등 개인 반복 습관 후보 포함)
    @Column(name = "patterns", columnDefinition = "JSON")
    private String patterns;

    // 레이어 5: 메타
    @Column(name = "mood_summary", columnDefinition = "TEXT")
    private String moodSummary;

    @Column(name = "tone", length = 20)
    private String tone;

    @CreationTimestamp
    @Column(name = "analyzed_at", nullable = false, updatable = false)
    private LocalDateTime analyzedAt;
}
