package momzzangseven.mztkbe.modules.verification.infrastructure.storage.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import momzzangseven.mztkbe.modules.verification.application.dto.StorageObjectStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

@ExtendWith(MockitoExtension.class)
class VerificationObjectStorageAdapterTest {

  @Mock private S3Client s3Client;

  private VerificationObjectStorageAdapter adapter;

  @BeforeEach
  void setUp() throws Exception {
    adapter = new VerificationObjectStorageAdapter(s3Client);
    Field field = VerificationObjectStorageAdapter.class.getDeclaredField("bucket");
    field.setAccessible(true);
    field.set(adapter, "bucket");
  }

  @Test
  void opensStreamFromObjectStorage() throws Exception {
    ResponseInputStream<GetObjectResponse> stream =
        new ResponseInputStream<>(
            GetObjectResponse.builder().contentLength(3L).contentType("image/jpeg").build(),
            AbortableInputStream.create(new ByteArrayInputStream(new byte[] {1, 2, 3})));
    when(s3Client.getObject(GetObjectRequest.builder().bucket("bucket").key("k").build()))
        .thenReturn(stream);

    try (StorageObjectStream opened = adapter.openStream("k")) {
      assertThat(opened.contentLength()).isEqualTo(3L);
      assertThat(opened.contentType()).isEqualTo("image/jpeg");
      assertThat(opened.stream().readAllBytes()).containsExactly(1, 2, 3);
    }
  }

  @Test
  void checksObjectExistence() {
    when(s3Client.headObject(HeadObjectRequest.builder().bucket("bucket").key("k").build()))
        .thenReturn(HeadObjectResponse.builder().build());

    assertThat(adapter.exists("k")).isTrue();
  }
}
