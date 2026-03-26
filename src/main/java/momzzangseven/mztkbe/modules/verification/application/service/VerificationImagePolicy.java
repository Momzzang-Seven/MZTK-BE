package momzzangseven.mztkbe.modules.verification.application.service;

import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.verification.application.config.VerificationRuntimeProperties;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VerificationImagePolicy {

  private final VerificationRuntimeProperties runtimeProperties;

  public boolean isHeifEnabled() {
    return runtimeProperties.heif().enabled();
  }

  public void validateOriginalSize(byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      throw new IllegalArgumentException("Image bytes are empty");
    }
    if (bytes.length > runtimeProperties.image().maxOriginalBytes()) {
      throw new IllegalArgumentException("Image size exceeds policy");
    }
  }

  public void validateObjectMetadata(long contentLength, String contentType, String extension) {
    if (contentLength <= 0) {
      throw new IllegalArgumentException("Image content length must be positive");
    }
    if (contentLength > runtimeProperties.image().maxOriginalBytes()) {
      throw new IllegalArgumentException("Image size exceeds policy");
    }
    if (contentType == null || contentType.isBlank()) {
      return;
    }

    String normalizedContentType = contentType.toLowerCase(Locale.ROOT);
    if (!normalizedContentType.startsWith("image/")
        && !normalizedContentType.equals("application/octet-stream")) {
      throw new IllegalArgumentException("Image content type is invalid");
    }
    if (normalizedContentType.equals("application/octet-stream")) {
      return;
    }

    Set<String> allowedContentTypes =
        switch (extension.toLowerCase(Locale.ROOT)) {
          case "jpg", "jpeg" -> Set.of("image/jpeg", "image/jpg", "image/pjpeg");
          case "png" -> Set.of("image/png");
          case "heic" -> Set.of("image/heic", "image/heic-sequence");
          case "heif" -> Set.of("image/heif", "image/heif-sequence");
          case "webp" -> Set.of("image/webp");
          default -> Set.of();
        };
    if (!allowedContentTypes.isEmpty() && !allowedContentTypes.contains(normalizedContentType)) {
      throw new IllegalArgumentException("Image content type does not match extension");
    }
  }

  public void validatePixels(BufferedImage image) {
    long pixels = (long) image.getWidth() * image.getHeight();
    if (pixels > runtimeProperties.image().maxPixels()) {
      throw new IllegalArgumentException("Image pixel count exceeds policy");
    }
  }
}
