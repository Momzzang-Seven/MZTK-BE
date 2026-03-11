package momzzangseven.mztkbe.modules.verification.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VerificationObjectStoragePortTest {

  @Test
  void storedObjectExposesFetchedObjectMetadata() {
    ObjectStoragePort.StoredObject storedObject =
        new ObjectStoragePort.StoredObject(
            "temp/verification/object.jpg", "image/jpeg", 2048L, new byte[] {1, 2, 3});

    assertThat(storedObject.objectKey()).isEqualTo("temp/verification/object.jpg");
    assertThat(storedObject.contentType()).isEqualTo("image/jpeg");
    assertThat(storedObject.sizeBytes()).isEqualTo(2048L);
    assertThat(storedObject.bytes()).containsExactly(1, 2, 3);
  }
}
