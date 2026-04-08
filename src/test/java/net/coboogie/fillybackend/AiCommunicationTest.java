package net.coboogie.fillybackend;

import net.coboogie.fillybackend.vo.AiAnalysisResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

class AiCommunicationTest {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String FASTAPI_URL = "http://localhost:8000/generate-caption";

    @Test
    @DisplayName("FastAPI BLIP 모델 이미지 캡셔닝 테스트")
    void blipModelTest() {
        // 1. 준비: 테스트할 이미지 파일 (실제 경로로 수정 필요)
        String filePath = "C:/test/images.jpg";
        File file = new File(filePath);

        // 파일 존재 여부 확인
        assertThat(file.exists()).isTrue();

        // 2. 요청 헤더 및 바디 설정
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(file));

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // 3. 실행: FastAPI 서버로 POST 요청
        ResponseEntity<AiAnalysisResponse> response = restTemplate.postForEntity(
                FASTAPI_URL,
                requestEntity,
                AiAnalysisResponse.class
        );

        // 4. 검증
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        AiAnalysisResponse result = response.getBody();
        System.out.println("분석된 파일명: " + result.getFilename());
        System.out.println("생성된 캡션: " + result.getCaption());

        // 결과값에 내용이 비어있지 않은지 확인
        assertThat(result.getCaption()).isNotEmpty();
    }
}