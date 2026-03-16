package momzzangseven.mztkbe.modules.verification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.verification.application.dto.ExifMetadataInfo;
import momzzangseven.mztkbe.modules.verification.application.dto.StorageObjectStream;
import momzzangseven.mztkbe.modules.verification.application.port.out.ExifMetadataPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.ObjectStoragePort;
import momzzangseven.mztkbe.modules.verification.application.port.out.PrepareOriginalImagePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VerificationSourceImageServiceTest {

  @Mock private ObjectStoragePort objectStoragePort;
  @Mock private ExifMetadataPort exifMetadataPort;
  @Mock private PrepareOriginalImagePort prepareOriginalImagePort;
  @Mock private VerificationImagePolicy verificationImagePolicy;

  private VerificationSourceImageService service;

  @BeforeEach
  void setUp() {
    service =
        new VerificationSourceImageService(
            objectStoragePort, exifMetadataPort, prepareOriginalImagePort, verificationImagePolicy);
  }

  @Test
  void extractsExifFromValidatedObjectStream() throws Exception {
    when(objectStoragePort.openStream("private/workout/a.jpg"))
        .thenReturn(
            new StorageObjectStream(new ByteArrayInputStream(new byte[] {1, 2}), 2L, "image/jpeg"));
    ExifMetadataInfo exif = new ExifMetadataInfo(LocalDateTime.of(2026, 3, 13, 9, 0));
    when(exifMetadataPort.extract(any())).thenReturn(Optional.of(exif));

    Optional<ExifMetadataInfo> result = service.extractExif("private/workout/a.jpg", "jpg");

    assertThat(result).contains(exif);
    verify(verificationImagePolicy).validateObjectMetadata(2L, "image/jpeg", "jpg");
  }

  @Test
  void wrapsPolicyViolationAsIOExceptionEvenWhenCloseFails() throws Exception {
    InputStream brokenCloseStream =
        new ByteArrayInputStream(new byte[] {1}) {
          @Override
          public void close() throws IOException {
            throw new IOException("close failed");
          }
        };
    when(objectStoragePort.openStream("private/workout/a.jpg"))
        .thenReturn(new StorageObjectStream(brokenCloseStream, 1L, "image/jpeg"));
    doThrow(new IllegalArgumentException("invalid"))
        .when(verificationImagePolicy)
        .validateObjectMetadata(1L, "image/jpeg", "jpg");

    assertThatThrownBy(() -> service.extractExif("private/workout/a.jpg", "jpg"))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("violates image policy");
  }
}
