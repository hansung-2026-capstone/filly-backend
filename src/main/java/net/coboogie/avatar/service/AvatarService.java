package net.coboogie.avatar.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.coboogie.avatar.dto.AvatarResponse;
import net.coboogie.avatar.repository.AvatarHistoryRepository;
import net.coboogie.diary.service.GcsStorageService;
import net.coboogie.persona.repository.PersonaSnapshotRepository;
import net.coboogie.user.repository.UserRepository;
import net.coboogie.vo.AvatarHistoryVO;
import net.coboogie.vo.PersonaSnapshotVO;
import net.coboogie.vo.UserVO;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 아바타 이미지 생성 서비스.
 * <p>
 * 사용자의 최신 페르소나를 기반으로 Gemini가 동물 종류·배경색·포즈를 결정하고,
 * BFL FLUX API로 아바타 이미지를 생성한 뒤 GCS에 업로드한다.
 * 생성이 완료되면 {@code UserVO.currentAvatarUrl}을 업데이트하고 이력을 저장한다.
 */
@Slf4j
@Service
public class AvatarService {

    private final AvatarHistoryRepository avatarHistoryRepository;
    private final PersonaSnapshotRepository personaSnapshotRepository;
    private final UserRepository userRepository;
    // private final BflImageService bflImageService;
    private final GcsStorageService gcsStorageService;
    private final ChatClient avatarChatClient;
    private final ObjectMapper objectMapper;

    /** AvatarService 생성자. */
    public AvatarService(
            AvatarHistoryRepository avatarHistoryRepository,
            PersonaSnapshotRepository personaSnapshotRepository,
            UserRepository userRepository,
            // BflImageService bflImageService,
            GcsStorageService gcsStorageService,
            @Qualifier("avatarChatClient") ChatClient avatarChatClient,
            @Qualifier("aiObjectMapper") ObjectMapper objectMapper) {
        this.avatarHistoryRepository = avatarHistoryRepository;
        this.personaSnapshotRepository = personaSnapshotRepository;
        this.userRepository = userRepository;
        // this.bflImageService = bflImageService;
        this.gcsStorageService = gcsStorageService;
        this.avatarChatClient = avatarChatClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 사용자의 최신 페르소나를 기반으로 아바타 이미지를 생성한다.
     * <p>
     * 1. 최신 페르소나 조회 → 2. Gemini로 동물·배경색·포즈 결정
     * → 3. BFL FLUX로 이미지 생성 → 4. GCS 업로드
     * → 5. AvatarHistoryVO 저장 및 사용자 아바타 URL 갱신
     *
     * @param userId JWT 인증 사용자 ID
     * @return 생성된 아바타 정보 (이력 ID + 서명 URL)
     * @throws NoSuchElementException 사용자 또는 페르소나가 존재하지 않는 경우
     * @throws IllegalStateException  이미지 생성 또는 업로드 실패 시
     */
    @Transactional
    public AvatarResponse generateAvatar(Long userId) {
        UserVO user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다: " + userId));

        PersonaSnapshotVO persona = personaSnapshotRepository.findLatestByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("아바타 생성에 필요한 페르소나가 없습니다. 먼저 페르소나를 생성해 주세요."));

        byte[] imageBytes;
        try {
            imageBytes = generateImageBytes(userId, persona);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("아바타 이미지 생성 중 인터럽트가 발생했습니다.", e);
        }

        String blobPath = gcsStorageService.uploadBytes(imageBytes, "avatars", "avatar.png", "image/png");
        String signedUrl = gcsStorageService.generateSignedUrl(blobPath);

        AvatarHistoryVO history = AvatarHistoryVO.builder()
                .user(user)
                .personaSnapshot(persona)
                .gcsUrl(blobPath)
                .status(AvatarHistoryVO.Status.COMPLETED)
                .build();
        AvatarHistoryVO saved = avatarHistoryRepository.save(history);

        user.setCurrentAvatarUrl(blobPath);
        log.info("아바타 생성 완료: userId={}, avatarHistoryId={}", userId, saved.getId());

        return new AvatarResponse(saved.getId(), signedUrl);
    }

    /**
     * 페르소나 생성 직후 비동기로 아바타를 생성한다.
     * <p>
     * {@code PersonaService}에서 트랜잭션 커밋 후 호출되므로,
     * 페르소나가 DB에 반드시 존재하는 상태에서 실행된다.
     * 실패 시 예외를 전파하지 않고 로그만 남긴다.
     *
     * @param userId            사용자 ID
     * @param personaSnapshotId 방금 생성된 페르소나 스냅샷 ID
     */
    @Async
    @Transactional
    public void generateAvatarAsync(Long userId, Long personaSnapshotId) {
        log.info("아바타 비동기 생성 시작: userId={}, personaSnapshotId={}", userId, personaSnapshotId);
        try {
            UserVO user = userRepository.findById(userId)
                    .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다: " + userId));

            PersonaSnapshotVO persona = personaSnapshotRepository.findById(personaSnapshotId)
                    .orElseThrow(() -> new NoSuchElementException("페르소나를 찾을 수 없습니다: " + personaSnapshotId));

            byte[] imageBytes = generateImageBytes(userId, persona);

            String blobPath = gcsStorageService.uploadBytes(imageBytes, "avatars", "avatar.png", "image/png");
            String signedUrl = gcsStorageService.generateSignedUrl(blobPath);

            AvatarHistoryVO history = AvatarHistoryVO.builder()
                    .user(user)
                    .personaSnapshot(persona)
                    .gcsUrl(blobPath)
                    .status(AvatarHistoryVO.Status.COMPLETED)
                    .build();
            avatarHistoryRepository.save(history);

            user.setCurrentAvatarUrl(blobPath);
            log.info("아바타 비동기 생성 완료: userId={}, signedUrl={}", userId, signedUrl);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("아바타 비동기 생성 중 인터럽트 발생: userId={}", userId, e);
        } catch (Exception e) {
            log.error("아바타 비동기 생성 실패: userId={}", userId, e);
        }
    }

    private byte[] generateImageBytes(Long userId, PersonaSnapshotVO persona) throws InterruptedException {
        AvatarParams params = resolveAvatarParams(persona);
        log.info("아바타 파라미터 결정: userId={}, animal={}, bgColor={}, pose={}",
                userId, params.animal(), params.backgroundColor(), params.pose());

        String fluxPrompt = buildFluxPrompt(params);
        // return bflImageService.generateImage(fluxPrompt);
        log.warn("BFL 서비스가 비활성화되어 있습니다. 빈 이미지를 반환합니다.");
        return new byte[0];
    }

    @SuppressWarnings("unchecked")
    private AvatarParams resolveAvatarParams(PersonaSnapshotVO persona) {
        String userMessage = "Persona Title: " + persona.getTitle() + "\nPersona Summary: " + persona.getSummary();
        String raw = avatarChatClient.prompt()
                .user(userMessage)
                .call()
                .content();

        raw = raw.replaceAll("(?s)```json\\s*", "").replaceAll("```", "").strip();
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            raw = raw.substring(start, end + 1);
        }

        try {
            Map<String, String> parsed = objectMapper.readValue(raw, Map.class);
            return new AvatarParams(
                    parsed.getOrDefault("animal", "cat"),
                    parsed.getOrDefault("backgroundColor", "#E8E8E8"),
                    parsed.getOrDefault("pose", "sitting quietly")
            );
        } catch (Exception e) {
            log.warn("아바타 파라미터 파싱 실패, 기본값 사용. raw={}", raw, e);
            return new AvatarParams("cat", "#E8E8E8", "sitting quietly");
        }
    }

    private String buildFluxPrompt(AvatarParams params) {
        return String.format(
                "cute chibi %s character, %s, solid %s background, "
                        + "simple flat 2D illustration, rounded shapes, soft pastel colors, "
                        + "white outline, clean design, profile avatar style, "
                        + "masterpiece, best quality, detailed illustration",
                params.animal(), params.pose(), params.backgroundColor()
        );
    }

    /**
     * Gemini가 결정한 아바타 시각 파라미터.
     *
     * @param animal          동물 종류 (turtle / cat / dog / capybara / bear)
     * @param backgroundColor 배경색 HEX 코드 (예: #FFD9A0)
     * @param pose            포즈 설명 (영어)
     */
    private record AvatarParams(String animal, String backgroundColor, String pose) {
    }
}
