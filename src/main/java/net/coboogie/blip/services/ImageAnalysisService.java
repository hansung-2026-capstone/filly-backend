package net.coboogie.blip.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coboogie.blip.dto.BlipAnalyzeResponse;
import net.coboogie.blip.dto.ImageAnalysisResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageAnalysisService {

    private final RestTemplate restTemplate;

    @Value("${fastapi.url}")
    private String fastapiUrl;

    public ImageAnalysisResult analyzeCaption(MultipartFile file) throws IOException {
        long startedAt = System.currentTimeMillis();
        log.info("BLIP analyze start url={} filename={} size={} contentType={}",
                fastapiUrl, file.getOriginalFilename(), file.getSize(), file.getContentType());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ByteArrayResource imageResource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", imageResource);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<BlipAnalyzeResponse> response;
        try {
            response = restTemplate.postForEntity(
                    fastapiUrl + "/blip-analyze",
                    requestEntity,
                    BlipAnalyzeResponse.class
            );
        } catch (RestClientResponseException e) {
            log.warn("BLIP analyze failed status={} body={} filename={}",
                    e.getStatusCode(), abbreviate(e.getResponseBodyAsString(), 500), file.getOriginalFilename(), e);
            throw e;
        }

        if (response.getBody() == null || response.getBody().getData() == null) {
            throw new IllegalStateException("BLIP API 응답이 비어 있습니다.");
        }
        BlipAnalyzeResponse.Data data = response.getBody().getData();
        log.info("BLIP analyze complete filename={} elapsedMs={} hasCaption={} hasMood={}",
                file.getOriginalFilename(), System.currentTimeMillis() - startedAt,
                data.getCaption() != null && !data.getCaption().isBlank(),
                data.getMood() != null && !data.getMood().isBlank());
        return new ImageAnalysisResult(data.getCaption(), data.getMood());
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
}
