package net.coboogie.blip.services;

import lombok.RequiredArgsConstructor;
import net.coboogie.blip.dto.BlipAnalyzeResponse;
import net.coboogie.blip.dto.ImageAnalysisResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class ImageAnalysisService {

    private final RestTemplate restTemplate;

    @Value("${fastapi.url}")
    private String fastapiUrl;

    public ImageAnalysisResult analyzeCaption(MultipartFile file) throws IOException {
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

        ResponseEntity<BlipAnalyzeResponse> response = restTemplate.postForEntity(
                fastapiUrl + "/blip-analyze",
                requestEntity,
                BlipAnalyzeResponse.class
        );

        BlipAnalyzeResponse.Data data = response.getBody().getData();
        return new ImageAnalysisResult(data.getCaption(), data.getMood());
    }
}
