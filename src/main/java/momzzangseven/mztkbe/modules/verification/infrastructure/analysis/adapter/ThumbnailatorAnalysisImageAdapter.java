package momzzangseven.mztkbe.modules.verification.infrastructure.analysis.adapter;

import dev.matrixlab.webp4j.WebPCodec;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
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
      Path originalPath, int maxLongEdge, double webpQuality) throws IOException {
    Path tempDir = originalPath.getParent();
    String normalizedExtension = extractExtension(originalPath);
    Path analysisPath = tempDir.resolve("analysis.webp");

    try {
      validateCodecAvailability(normalizedExtension);
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
      return PreparedAnalysisImage.noop(analysisPath);
    } catch (IllegalArgumentException validationException) {
      cleanup(analysisPath);
      throw new IOException(validationException.getMessage(), validationException);
    } catch (IOException | RuntimeException ex) {
      cleanup(analysisPath);
      throw ex;
    }
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

  private String extractExtension(Path originalPath) {
    String filename = originalPath.getFileName() == null ? "" : originalPath.getFileName().toString();
    int dot = filename.lastIndexOf('.');
    if (dot < 0 || dot == filename.length() - 1) {
      throw new IllegalArgumentException("Original image extension is required");
    }
    return normalizeExtension(filename.substring(dot + 1));
  }

  private void cleanup(Path analysisPath) {
    deleteWithWarning(analysisPath);
  }

  private void deleteWithWarning(Path path) {
    try {
      deletePathIfExists(path);
    } catch (IOException ex) {
      log.warn("Failed to delete verification temp path: {}", path, ex);
    }
  }
}
