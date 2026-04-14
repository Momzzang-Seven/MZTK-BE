package momzzangseven.mztkbe.modules.verification.application.service;

import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.verification.application.dto.ExifMetadataInfo;
import momzzangseven.mztkbe.modules.verification.application.dto.PreparedOriginalImage;
import momzzangseven.mztkbe.modules.verification.application.dto.StorageObjectStream;
import momzzangseven.mztkbe.modules.verification.application.port.out.ExifMetadataPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.ObjectStoragePort;
import momzzangseven.mztkbe.modules.verification.application.port.out.PrepareOriginalImagePort;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class VerificationSourceImageService {

  private final ObjectStoragePort objectStoragePort;
  private final ExifMetadataPort exifMetadataPort;
  private final PrepareOriginalImagePort prepareOriginalImagePort;
  private final VerificationImagePolicy verificationImagePolicy;

  public Optional<ExifMetadataInfo> extractExif(String objectKey, String extension)
      throws IOException {
    try (StorageObjectStream objectStream = openValidatedObjectStream(objectKey, extension)) {
      return exifMetadataPort.extract(objectStream.stream());
    }
  }

  public PreparedOriginalImage prepareOriginalImage(String objectKey, String extension)
      throws IOException {
    return prepareOriginalImagePort.prepare(objectKey, extension);
  }

  private StorageObjectStream openValidatedObjectStream(String objectKey, String extension)
      throws IOException {
    StorageObjectStream objectStream = objectStoragePort.openStream(objectKey);
    try {
      verificationImagePolicy.validateObjectMetadata(
          objectStream.contentLength(), objectStream.contentType(), extension);
      return objectStream;
    } catch (RuntimeException ex) {
      try {
        objectStream.close();
      } catch (IOException closeEx) {
        log.warn("Failed to close verification object stream: {}", objectKey, closeEx);
      }
      throw new IOException("Stored object violates image policy", ex);
    }
  }
}
