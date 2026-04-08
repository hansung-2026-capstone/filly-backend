package net.coboogie.fillybackend.clip.services;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import java.io.File;

public class BlipAnalysisService {

    public void generateImageCaption(String filePath) {
        // 1. BLIP 엔드포인트로 변경
        String url = "http://localhost:8000/generate-caption";
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(new File(filePath)));

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            // 2. 응답 받기
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);

            // 결과 예시: {"filename": "test.jpg", "caption": "a dog playing with a ball"}
            System.out.println("BLIP Result: " + response.getBody());
        } catch (Exception e) {
            System.err.println("통신 에러: " + e.getMessage());
        }
    }
}