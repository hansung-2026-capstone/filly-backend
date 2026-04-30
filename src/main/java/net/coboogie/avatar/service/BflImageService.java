package net.coboogie.avatar.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Black Forest Labs FLUX API를 사용한 이미지 생성 서비스.
 * <p>
 * 이미지 생성 작업을 제출하고 완료될 때까지 폴링한 뒤 이미지 바이트를 반환한다.
 * BFL API는 비동기 방식으로 동작하며, 생성에 보통 10~30초가 소요된다.
 */
@Slf4j
@Service
public class BflImageService {

    private static final String BFL_BASE_URL = "https://api.us1.bfl.ai/v1";
    private static final String BFL_GENERATE_URL = BFL_BASE_URL + "/flux-pro-1.1";
    private static final String BFL_POLL_URL = BFL_BASE_URL + "/get_result";
    private static final int MAX_POLL_ATTEMPTS = 30;
    private static final int POLL_INTERVAL_MS = 3000;
    private static final String STATUS_READY = "Ready";
    private static final String STATUS_ERROR = "Error";

    private final RestTemplate restTemplate;

    @Value("${external.bfl.api-key}")
    private String bflApiKey;

    /** BflImageService 생성자. */
    public BflImageService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 주어진 프롬프트로 이미지를 생성하고 PNG 바이트 배열로 반환한다.
     *
     * @param prompt 영어 이미지 생성 프롬프트
     * @return 생성된 이미지 바이트 배열
     * @throws InterruptedException     폴링 대기 중 스레드 인터럽트 발생 시
     * @throws IllegalStateException    이미지 생성 실패 또는 시간 초과 시
     */
    public byte[] generateImage(String prompt) throws InterruptedException {
        String taskId = submitJob(prompt);
        log.info("BFL 이미지 생성 작업 제출 완료: taskId={}", taskId);

        String imageUrl = pollUntilReady(taskId);
        log.info("BFL 이미지 생성 완료: taskId={}, imageUrl={}", taskId, imageUrl);

        return downloadImage(imageUrl);
    }

    @SuppressWarnings("unchecked")
    private String submitJob(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-key", bflApiKey);

        Map<String, Object> body = Map.of(
                "prompt", prompt,
                "width", 1024,
                "height", 1024
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(BFL_GENERATE_URL, request, Map.class);

        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null || !responseBody.containsKey("id")) {
            throw new IllegalStateException("BFL API로부터 task ID를 받지 못했습니다.");
        }

        return (String) responseBody.get("id");
    }

    @SuppressWarnings("unchecked")
    private String pollUntilReady(String taskId) throws InterruptedException {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-key", bflApiKey);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        String pollUrl = BFL_POLL_URL + "?id=" + taskId;

        for (int i = 0; i < MAX_POLL_ATTEMPTS; i++) {
            Thread.sleep(POLL_INTERVAL_MS);

            ResponseEntity<Map> response = restTemplate.exchange(pollUrl, HttpMethod.GET, request, Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null) {
                continue;
            }

            String status = (String) body.get("status");
            log.debug("BFL 폴링: taskId={}, status={}, attempt={}/{}", taskId, status, i + 1, MAX_POLL_ATTEMPTS);

            if (STATUS_READY.equals(status)) {
                Map<String, Object> result = (Map<String, Object>) body.get("result");
                return (String) result.get("sample");
            }

            if (STATUS_ERROR.equals(status)) {
                throw new IllegalStateException("BFL 이미지 생성 실패: " + body);
            }
        }

        throw new IllegalStateException("BFL 이미지 생성 시간 초과: taskId=" + taskId);
    }

    private byte[] downloadImage(String imageUrl) {
        ResponseEntity<byte[]> response = restTemplate.getForEntity(imageUrl, byte[].class);
        if (response.getBody() == null) {
            throw new IllegalStateException("BFL 이미지 다운로드 실패: 응답 바디가 비어 있습니다.");
        }
        return response.getBody();
    }
}
