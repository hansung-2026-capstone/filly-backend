package net.coboogie.fillybackend.vo;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_diary_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiDiaryResultVO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diary_id", nullable = false)
    private DiaryEntryVO diary;

    @Column(name = "generated_text", nullable = false, columnDefinition = "TEXT")
    private String generatedText;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
