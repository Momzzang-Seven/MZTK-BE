package momzzangseven.mztkbe.modules.verification.application.service;

import java.awt.image.BufferedImage;
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

  public void validatePixels(BufferedImage image) {
    long pixels = (long) image.getWidth() * image.getHeight();
    if (pixels > runtimeProperties.image().maxPixels()) {
      throw new IllegalArgumentException("Image pixel count exceeds policy");
    }
  }
}
