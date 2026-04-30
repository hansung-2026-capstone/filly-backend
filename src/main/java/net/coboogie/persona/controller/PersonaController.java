package net.coboogie.persona.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import net.coboogie.common.response.ApiResponse;
import net.coboogie.persona.dto.PersonaResponse;
import net.coboogie.persona.service.PersonaService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 페르소나 조회 API 컨트롤러.
 * <p>
 * 프로필 화면 진입 시 호출되며, 생성 조건 충족 시 자동으로 새 페르소나를 생성하고 이력을 반환한다.
 */
@Tag(name = "Persona", description = "페르소나 조회 API")
@RestController
@RequestMapping("/v1/personas")
@RequiredArgsConstructor
public class PersonaController {

    private final PersonaService personaService;

    /**
     * 페르소나 이력 조회 API.
     * <p>
     * 생성 조건(최근 30일 일기 5개 이상 + 7일 경과)을 충족하면 새 페르소나를 자동 생성 후 이력을 반환한다.
     * 조건 미충족 시에는 기존 이력만 반환한다.
     *
     * @param userId JWT 인증 사용자 ID
     * @return 페르소나 이력 목록 (최신순)
     */
    @GetMapping
    @Operation(summary = "페르소나 이력 조회", description = "프로필 화면 진입 시 호출. 조건 충족 시 새 페르소나 자동 생성 후 이력 반환.")
    public ResponseEntity<ApiResponse<List<PersonaResponse>>> getPersonas(
            @AuthenticationPrincipal Long userId) {
        List<PersonaResponse> result = personaService.getPersonasWithAutoGenerate(userId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
