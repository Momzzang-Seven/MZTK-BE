package momzzangseven.mztkbe.modules.verification.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class VerificationObjectStoragePortTest {

  @Test
  void storedObjectExposesUploadedObjectMetadata() {
    LocalDateTime expiresAt = LocalDateTime.of(2026, 3, 8, 11, 0);
    ObjectStoragePort.StoredObject storedObject =
        new ObjectStoragePort.StoredObject("temp/verification/object.jpg", expiresAt);

    assertThat(storedObject.objectKey()).isEqualTo("temp/verification/object.jpg");
    assertThat(storedObject.expiresAt()).isEqualTo(expiresAt);
  }
}
