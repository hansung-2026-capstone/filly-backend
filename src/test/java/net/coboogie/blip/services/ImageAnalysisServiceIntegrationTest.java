package net.coboogie.blip.services;

import net.coboogie.blip.dto.ImageAnalysisResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ImageAnalysisService 통합 테스트
 *
 * 전제 조건:
 *   1. FastAPI 서버가 http://localhost:8000 에서 실행 중이어야 합니다.
 *      (filly-fastapi 레포 참조: docker run -d -p 8000:8000 filly-fastapi-server)
 *   2. src/test/resources/test.jpg 이미지 파일이 존재해야 합니다.
 */
class ImageAnalysisServiceIntegrationTest {

    private static final String FASTAPI_URL = "http://localhost:8000";

    private ImageAnalysisService imageAnalysisService;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        imageAnalysisService = new ImageAnalysisService(restTemplate);
        ReflectionTestUtils.setField(imageAnalysisService, "fastapiUrl", FASTAPI_URL);
    }

    @Test
    @DisplayName("FastAPI /blip-analyze 실서버 연동: 이미지에서 캡션 문자열 반환")
    void analyzeCaption_withRealImage_returnsNonBlankCaption() throws Exception {
        // given
        InputStream imageStream = getClass().getClassLoader().getResourceAsStream("test.jpg");
        assertThat(imageStream)
                .as("test.jpg 파일이 src/test/resources/ 에 존재해야 합니다.")
                .isNotNull();

        byte[] imageBytes = imageStream.readAllBytes();
        MockMultipartFile mockFile = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                imageBytes
        );

        // when
        ImageAnalysisResult result = imageAnalysisService.analyzeCaption(mockFile);

        // then
        System.out.println("추출된 캡션: " + result.caption());
        System.out.println("추출된 무드: " + result.mood());
        assertThat(result.caption()).isNotBlank();
        assertThat(result.mood()).isNotBlank();
    }
}
