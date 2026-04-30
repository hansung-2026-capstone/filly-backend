package net.coboogie.avatar.service;

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

import java.util.NoSuchElementException;

/**
 * 아바타 이미지 생성 서비스.
 * <p>
 * 사용자의 최신 페르소나를 기반으로 Gemini가 이미지 프롬프트를 생성하고,
 * BFL FLUX API로 아바타 이미지를 생성한 뒤 GCS에 업로드한다.
 * 생성이 완료되면 {@code UserVO.currentAvatarUrl}을 업데이트하고 이력을 저장한다.
 */
@Slf4j
@Service
public class AvatarService {

    private final AvatarHistoryRepository avatarHistoryRepository;
    private final PersonaSnapshotRepository personaSnapshotRepository;
    private final UserRepository userRepository;
    private final BflImageService bflImageService;
    private final GcsStorageService gcsStorageService;
    private final ChatClient avatarChatClient;

    /** AvatarService 생성자. */
    public AvatarService(
            AvatarHistoryRepository avatarHistoryRepository,
            PersonaSnapshotRepository personaSnapshotRepository,
            UserRepository userRepository,
            BflImageService bflImageService,
            GcsStorageService gcsStorageService,
            @Qualifier("avatarChatClient") ChatClient avatarChatClient) {
        this.avatarHistoryRepository = avatarHistoryRepository;
        this.personaSnapshotRepository = personaSnapshotRepository;
        this.userRepository = userRepository;
        this.bflImageService = bflImageService;
        this.gcsStorageService = gcsStorageService;
        this.avatarChatClient = avatarChatClient;
    }

    /**
     * 사용자의 최신 페르소나를 기반으로 아바타 이미지를 생성한다.
     * <p>
     * 1. 최신 페르소나 조회 → 2. Gemini로 이미지 프롬프트 생성
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

        String imagePrompt = buildImagePrompt(persona);
        log.info("아바타 이미지 프롬프트 생성 완료: userId={}", userId);

        byte[] imageBytes;
        try {
            imageBytes = bflImageService.generateImage(imagePrompt);
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

            String imagePrompt = buildImagePrompt(persona);

            byte[] imageBytes = bflImageService.generateImage(imagePrompt);

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

    private String buildImagePrompt(PersonaSnapshotVO persona) {
        String userMessage = "Persona Title: " + persona.getTitle() + "\nPersona Summary: " + persona.getSummary();
        return avatarChatClient.prompt()
                .user(userMessage)
                .call()
                .content();
    }
}
