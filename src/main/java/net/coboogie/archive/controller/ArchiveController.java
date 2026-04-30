package net.coboogie.archive.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import net.coboogie.archive.dto.ArchiveDiaryAddRequest;
import net.coboogie.archive.dto.ArchiveFolderCreateRequest;
import net.coboogie.archive.dto.ArchiveFolderResponse;
import net.coboogie.archive.dto.ArchiveFolderUpdateRequest;
import net.coboogie.archive.service.ArchiveService;
import net.coboogie.common.response.ApiResponse;
import net.coboogie.diary.dto.DiaryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 아카이브 폴더 REST API 컨트롤러.
 * Base URL: {@code /api/v1/archives}
 * <p>
 * 폴더 CRUD 및 폴더 내 일기 추가·조회·제거를 처리한다.
 */
@Tag(name = "Archive", description = "아카이브 폴더 API")
@RestController
@RequestMapping("/v1/archives")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ArchiveController {

    private final ArchiveService archiveService;

    /**
     * 아카이브 폴더를 생성한다.
     *
     * @param userId  JWT 인증 사용자 ID
     * @param request 폴더 이름·색상
     * @return 생성된 폴더 정보
     */
    @PostMapping
    @Operation(summary = "폴더 생성", description = "아카이브 폴더를 생성합니다.")
    public ResponseEntity<ApiResponse<ArchiveFolderResponse>> createFolder(
            @AuthenticationPrincipal Long userId,
            @RequestBody ArchiveFolderCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(archiveService.createFolder(userId, request)));
    }

    /**
     * 사용자의 폴더 목록을 일기 수와 함께 반환한다.
     *
     * @param userId JWT 인증 사용자 ID
     * @return 폴더 목록 (최신순)
     */
    @GetMapping
    @Operation(summary = "폴더 목록 조회", description = "폴더 목록과 각 폴더의 일기 수를 반환합니다.")
    public ResponseEntity<ApiResponse<List<ArchiveFolderResponse>>> getFolders(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(archiveService.getFolders(userId)));
    }

    /**
     * 폴더 이름 또는 색상을 수정한다.
     *
     * @param userId   JWT 인증 사용자 ID
     * @param folderId 수정할 폴더 ID
     * @param request  변경할 이름·색상 (null인 필드는 기존 값 유지)
     * @return 수정된 폴더 정보
     */
    @PatchMapping("/{folderId}")
    @Operation(summary = "폴더 수정", description = "폴더 이름 또는 색상을 수정합니다. null 필드는 기존 값 유지.")
    public ResponseEntity<ApiResponse<ArchiveFolderResponse>> updateFolder(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long folderId,
            @RequestBody ArchiveFolderUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(archiveService.updateFolder(userId, folderId, request)));
    }

    /**
     * 폴더를 삭제한다. 폴더 내 일기 연결도 함께 삭제된다.
     *
     * @param userId   JWT 인증 사용자 ID
     * @param folderId 삭제할 폴더 ID
     */
    @DeleteMapping("/{folderId}")
    @Operation(summary = "폴더 삭제", description = "폴더를 삭제합니다. 일기 자체는 삭제되지 않고 연결만 제거됩니다.")
    public ResponseEntity<ApiResponse<Void>> deleteFolder(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long folderId) {
        archiveService.deleteFolder(userId, folderId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /**
     * 폴더 내 일기 목록을 반환한다.
     *
     * @param userId   JWT 인증 사용자 ID
     * @param folderId 조회할 폴더 ID
     * @return 폴더 내 일기 목록 (추가일 내림차순)
     */
    @GetMapping("/{folderId}/diaries")
    @Operation(summary = "폴더 내 일기 목록", description = "폴더에 추가된 일기 목록을 반환합니다.")
    public ResponseEntity<ApiResponse<List<DiaryResponse>>> getDiariesInFolder(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long folderId) {
        return ResponseEntity.ok(ApiResponse.ok(archiveService.getDiariesInFolder(userId, folderId)));
    }

    /**
     * 폴더에 일기를 추가한다.
     *
     * @param userId   JWT 인증 사용자 ID
     * @param folderId 대상 폴더 ID
     * @param request  추가할 일기 ID
     */
    @PostMapping("/{folderId}/diaries")
    @Operation(summary = "폴더에 일기 추가", description = "같은 일기를 여러 폴더에 추가할 수 있습니다.")
    public ResponseEntity<ApiResponse<Void>> addDiaryToFolder(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long folderId,
            @RequestBody ArchiveDiaryAddRequest request) {
        archiveService.addDiaryToFolder(userId, folderId, request);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /**
     * 폴더에서 일기를 제거한다. 일기 자체는 삭제되지 않는다.
     *
     * @param userId   JWT 인증 사용자 ID
     * @param folderId 대상 폴더 ID
     * @param diaryId  제거할 일기 ID
     */
    @DeleteMapping("/{folderId}/diaries/{diaryId}")
    @Operation(summary = "폴더에서 일기 제거", description = "폴더에서 일기를 제거합니다. 일기 자체는 삭제되지 않습니다.")
    public ResponseEntity<ApiResponse<Void>> removeDiaryFromFolder(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long folderId,
            @PathVariable Long diaryId) {
        archiveService.removeDiaryFromFolder(userId, folderId, diaryId);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
