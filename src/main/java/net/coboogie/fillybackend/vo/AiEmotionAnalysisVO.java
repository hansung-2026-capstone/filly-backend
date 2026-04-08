package net.coboogie.fillybackend.vo;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_emotion_analysis")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiEmotionAnalysisVO {

    public enum EmotionType {
        CALM, HAPPY, ANXIOUS, SAD, ANGRY
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diary_id", nullable = false)
    private DiaryEntryVO diary;

    @Enumerated(EnumType.STRING)
    @Column(name = "emotion_type", nullable = false)
    private EmotionType emotionType;

    @Column(name = "score", nullable = false)
    private Float score;

    @Column(name = "mood_index", nullable = false)
    private Integer moodIndex;

    @Column(name = "detected_keywords", columnDefinition = "JSON")
    private String detectedKeywords;

    @CreationTimestamp
    @Column(name = "analyzed_at", nullable = false, updatable = false)
    private LocalDateTime analyzedAt;
}
