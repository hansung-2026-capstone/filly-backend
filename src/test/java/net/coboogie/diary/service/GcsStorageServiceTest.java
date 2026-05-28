package net.coboogie.diary.service;

import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GcsStorageServiceTest {

    @Mock private Storage storage;

    private GcsStorageService sut;

    @BeforeEach
    void setUp() {
        sut = new GcsStorageService(storage);
        ReflectionTestUtils.setField(sut, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(sut, "storageEnabled", true);
    }

    @Test
    @DisplayName("같은 blob의 서명 URL은 캐시된 값을 재사용한다")
    void givenSameBlobName_whenGenerateSignedUrlTwice_thenReuseCachedUrl() throws MalformedURLException {
        // given
        String blobName = "uploads/images/a.png";
        String signedUrl = "https://signed.example/a.png";
        given(storage.signUrl(
                any(BlobInfo.class),
                eq(1L),
                eq(TimeUnit.HOURS),
                any(Storage.SignUrlOption.class)
        )).willReturn(URI.create(signedUrl).toURL());

        // when
        String first = sut.generateSignedUrl(blobName);
        String second = sut.generateSignedUrl(blobName);

        // then
        assertThat(first).isEqualTo(signedUrl);
        assertThat(second).isEqualTo(signedUrl);
        verify(storage, times(1)).signUrl(
                any(BlobInfo.class),
                eq(1L),
                eq(TimeUnit.HOURS),
                any(Storage.SignUrlOption.class)
        );
    }
}
