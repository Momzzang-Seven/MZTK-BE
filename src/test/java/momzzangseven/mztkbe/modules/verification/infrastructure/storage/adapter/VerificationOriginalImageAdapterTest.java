package momzzangseven.mztkbe.modules.verification.infrastructure.storage.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import momzzangseven.mztkbe.modules.verification.application.config.VerificationRuntimeProperties;
import momzzangseven.mztkbe.modules.verification.application.dto.StorageObjectStream;
import momzzangseven.mztkbe.modules.verification.application.port.out.ObjectStoragePort;
import momzzangseven.mztkbe.modules.verification.application.service.VerificationImagePolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VerificationOriginalImageAdapterTest {

  @Mock private ObjectStoragePort objectStoragePort;

  @Test
  void preparesOriginalImageInTempWorkspaceAndDeletesItOnClose() throws Exception {
    byte[] bytes = new byte[] {1, 2, 3};
    when(objectStoragePort.openStream("k"))
        .thenReturn(new StorageObjectStream(new ByteArrayInputStream(bytes), 3L, "image/png"));

    VerificationOriginalImageAdapter adapter =
        new VerificationOriginalImageAdapter(objectStoragePort, imagePolicy(5242880L, 25000000L));

    Path originalPath;
    Path tempDir;
    try (var prepared = adapter.prepare("k", "png")) {
      originalPath = prepared.path();
      tempDir = originalPath.getParent();
      assertThat(Files.exists(originalPath)).isTrue();
      assertThat(Files.readAllBytes(originalPath)).containsExactly(bytes);
      assertThat(originalPath.getFileName().toString()).isEqualTo("original.png");
    }

    assertThat(Files.exists(originalPath)).isFalse();
    assertThat(Files.exists(tempDir)).isFalse();
  }

  @Test
  void rejectsInvalidStoredObjectMetadata() throws Exception {
    when(objectStoragePort.openStream("k"))
        .thenReturn(
            new StorageObjectStream(new ByteArrayInputStream(new byte[] {1, 2, 3}), 3L, "text/plain"));

    VerificationOriginalImageAdapter adapter =
        new VerificationOriginalImageAdapter(objectStoragePort, imagePolicy(5242880L, 25000000L));

    assertThatThrownBy(() -> adapter.prepare("k", "png"))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Stored object violates image policy");
  }

  private VerificationImagePolicy imagePolicy(long maxOriginalBytes, long maxPixels) {
    return new VerificationImagePolicy(
        new VerificationRuntimeProperties(
            new VerificationRuntimeProperties.Ai("gemini-2.5-flash-lite", 12, 2, false),
            new VerificationRuntimeProperties.Heif(true, "requires-imageio-plugin"),
            new VerificationRuntimeProperties.Image(maxOriginalBytes, maxPixels)));
  }
}
