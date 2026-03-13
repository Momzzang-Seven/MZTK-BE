package momzzangseven.mztkbe.modules.verification.infrastructure.analysis.adapter;

import dev.matrixlab.webp4j.WebPCodec;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.verification.application.dto.PreparedAnalysisImage;
import momzzangseven.mztkbe.modules.verification.application.port.out.ImageCodecSupportPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.PrepareAnalysisImagePort;
import momzzangseven.mztkbe.modules.verification.application.service.VerificationImagePolicy;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ThumbnailatorAnalysisImageAdapter implements PrepareAnalysisImagePort {

  private final VerificationImagePolicy verificationImagePolicy;
  private final ImageCodecSupportPort imageCodecSupportPort;

  @Override
  public PreparedAnalysisImage prepare(
      byte[] bytes, String extension, int maxLongEdge, double webpQuality) throws IOException {
    Path tempDir = createRequestTempDirectory();
    String normalizedExtension = normalizeExtension(extension);
    Path originalPath = tempDir.resolve("original." + normalizedExtension);
    Path analysisPath = tempDir.resolve("analysis.webp");

    try {
      validateCodecAvailability(normalizedExtension);
      Files.write(originalPath, bytes, StandardOpenOption.CREATE_NEW);
      BufferedImage source = ImageIO.read(originalPath.toFile());
      if (source == null) {
        throw new IllegalArgumentException("Unable to decode source image");
      }
      verificationImagePolicy.validatePixels(source);

      int width = source.getWidth();
      int height = source.getHeight();
      int longEdge = Math.max(width, height);
      double scale = longEdge > maxLongEdge ? (double) maxLongEdge / longEdge : 1.0d;
      int targetWidth = Math.max(1, (int) Math.round(width * scale));
      int targetHeight = Math.max(1, (int) Math.round(height * scale));

      BufferedImage analysisImage =
          Thumbnails.of(source).size(targetWidth, targetHeight).asBufferedImage();
      byte[] encodedWebp = WebPCodec.encodeImage(analysisImage, (float) (webpQuality * 100.0d));
      Files.write(analysisPath, encodedWebp, StandardOpenOption.CREATE_NEW);
      return new PreparedAnalysisImage(
          analysisPath, () -> cleanup(analysisPath, originalPath, tempDir));
    } catch (IllegalArgumentException validationException) {
      cleanup(analysisPath, originalPath, tempDir);
      throw new IOException(validationException.getMessage(), validationException);
    } catch (IOException | RuntimeException ex) {
      cleanup(analysisPath, originalPath, tempDir);
      throw ex;
    }
  }

  protected Path createRequestTempDirectory() throws IOException {
    Path root = Path.of(System.getProperty("java.io.tmpdir"), "mztk", "verification");
    Files.createDirectories(root);
    Path requestDir = root.resolve(UUID.randomUUID().toString());
    Files.createDirectory(requestDir);
    return requestDir;
  }

  protected void deletePathIfExists(Path path) throws IOException {
    Files.deleteIfExists(path);
  }

  private void validateCodecAvailability(String extension) throws IOException {
    if (("heic".equals(extension) || "heif".equals(extension))
        && !imageCodecSupportPort.isHeifDecodeAvailable()) {
      throw new IOException("HEIF/HEIC decoder is not available in the runtime");
    }
    if (!imageCodecSupportPort.isWebpWriteAvailable()) {
      throw new IOException("WebP writer is not available in the runtime");
    }
  }

  private String normalizeExtension(String extension) {
    if (extension == null || extension.isBlank()) {
      throw new IllegalArgumentException("Image extension is required");
    }
    return extension.toLowerCase(Locale.ROOT);
  }

  private void cleanup(Path analysisPath, Path originalPath, Path tempDir) {
    deleteWithWarning(analysisPath);
    deleteWithWarning(originalPath);
    deleteWithWarning(tempDir);
  }

  private void deleteWithWarning(Path path) {
    try {
      deletePathIfExists(path);
    } catch (IOException ex) {
      log.warn("Failed to delete verification temp path: {}", path, ex);
    }
  }
}
