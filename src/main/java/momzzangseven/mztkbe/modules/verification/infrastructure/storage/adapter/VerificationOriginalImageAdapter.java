package momzzangseven.mztkbe.modules.verification.infrastructure.storage.adapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.verification.application.dto.PreparedOriginalImage;
import momzzangseven.mztkbe.modules.verification.application.dto.StorageObjectStream;
import momzzangseven.mztkbe.modules.verification.application.port.out.ObjectStoragePort;
import momzzangseven.mztkbe.modules.verification.application.port.out.PrepareOriginalImagePort;
import momzzangseven.mztkbe.modules.verification.application.service.VerificationImagePolicy;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class VerificationOriginalImageAdapter implements PrepareOriginalImagePort {

  private final ObjectStoragePort objectStoragePort;
  private final VerificationImagePolicy verificationImagePolicy;

  @Override
  public PreparedOriginalImage prepare(String objectKey, String extension) throws IOException {
    Path tempDir = createRequestTempDirectory();
    Path originalPath = tempDir.resolve("original." + extension);

    try (StorageObjectStream objectStream = objectStoragePort.openStream(objectKey)) {
      verificationImagePolicy.validateObjectMetadata(
          objectStream.contentLength(), objectStream.contentType(), extension);
      Files.copy(objectStream.stream(), originalPath, StandardCopyOption.REPLACE_EXISTING);
      return new PreparedOriginalImage(originalPath, () -> cleanupWorkspace(originalPath, tempDir));
    } catch (IOException ex) {
      cleanupWorkspace(originalPath, tempDir);
      throw ex;
    } catch (RuntimeException ex) {
      cleanupWorkspace(originalPath, tempDir);
      throw new IOException("Stored object violates image policy", ex);
    }
  }

  protected void deletePathIfExists(Path path) throws IOException {
    Files.deleteIfExists(path);
  }

  private Path createRequestTempDirectory() throws IOException {
    Path root = Path.of(System.getProperty("java.io.tmpdir"), "mztk", "verification");
    Files.createDirectories(root);
    Path requestDir = root.resolve(UUID.randomUUID().toString());
    Files.createDirectory(requestDir);
    return requestDir;
  }

  private void cleanupWorkspace(Path originalPath, Path tempDir) {
    deleteWithWarning(originalPath);
    deleteWithWarning(tempDir);
  }

  private void deleteWithWarning(Path path) {
    if (path == null) {
      return;
    }
    try {
      deletePathIfExists(path);
    } catch (IOException ex) {
      log.warn("Failed to delete verification temp path: {}", path, ex);
    }
  }
}
