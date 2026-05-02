package net.coboogie.avatar.service;

import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Vertex AI Imagen API를 사용한 이미지 생성 서비스.
 * <p>
 * ADC(Application Default Credentials)로 인증하여 Vertex AI Imagen에 이미지 생성을 요청하고,
 * Base64 인코딩된 응답을 디코딩하여 바이트 배열로 반환한다.
 */
@Slf4j
@Service
public class VertexAiImagenService {

    private static final String ENDPOINT_TEMPLATE =
            "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s"
                    + "/publishers/google/models/%s:predict";
    private static final String GCP_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

    private final RestTemplate restTemplate;

    @Value("${spring.cloud.gcp.project-id}")
    private String projectId;

    @Value("${spring.ai.vertex.ai.gemini.location}")
    private String location;

    @Value("${vertex.ai.imagen.model}")
    private String model;

    /** VertexAiImagenService 생성자. */
    public VertexAiImagenService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 주어진 프롬프트로 이미지를 생성하고 PNG 바이트 배열로 반환한다.
     *
     * @param prompt 영어 이미지 생성 프롬프트
     * @return 생성된 이미지 바이트 배열
     * @throws IOException 인증 토큰 획득 실패 시
     * @throws IllegalStateException 이미지 생성 실패 시
     */
    public byte[] generateImage(String prompt) throws IOException {
        String accessToken = getAccessToken();
        String url = String.format(ENDPOINT_TEMPLATE, location, projectId, location, model);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        Map<String, Object> body = Map.of(
                "instances", List.of(Map.of("prompt", prompt)),
                "parameters", Map.of("sampleCount", 1, "aspectRatio", "1:1")
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        log.info("Vertex AI Imagen 요청: model={}, location={}", model, location);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);

        return parseImageBytes(response);
    }

    private String getAccessToken() throws IOException {
        GoogleCredentials credentials = GoogleCredentials
                .getApplicationDefault()
                .createScoped(Collections.singleton(GCP_SCOPE));
        credentials.refreshIfExpired();
        return credentials.getAccessToken().getTokenValue();
    }

    @SuppressWarnings("unchecked")
    private byte[] parseImageBytes(Map<String, Object> response) {
        if (response == null) {
            throw new IllegalStateException("Vertex AI Imagen API 응답이 비어 있습니다.");
        }

        List<Map<String, Object>> predictions = (List<Map<String, Object>>) response.get("predictions");
        if (predictions == null || predictions.isEmpty()) {
            throw new IllegalStateException("Vertex AI Imagen API 응답에 predictions가 없습니다.");
        }

        String base64Image = (String) predictions.get(0).get("bytesBase64Encoded");
        if (base64Image == null) {
            throw new IllegalStateException("Vertex AI Imagen API 응답에 이미지 데이터가 없습니다.");
        }

        log.info("Vertex AI Imagen 이미지 생성 완료");
        return Base64.getDecoder().decode(base64Image);
    }
}
