package momzzangseven.mztkbe.modules.image.infrastructure.config;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.image.application.port.out.ImageStoragePort;
import org.springframework.stereotype.Component;

/** Infrastructure adapter that builds full S3 image URLs from object keys. */
@Component
@RequiredArgsConstructor
public class ImageStorageAdapter implements ImageStoragePort {

  private final ImageStorageProperties properties;

  @Override
  public String buildImageUrl(String objectKey) {
    if (objectKey == null || objectKey.isBlank()) {
      return null;
    }
    String prefix = properties.getUrlPrefix();
    String normalizedPrefix = prefix.endsWith("/") ? prefix : prefix + "/";
    String normalizedKey = objectKey.startsWith("/") ? objectKey.substring(1) : objectKey;
    return normalizedPrefix + normalizedKey;
  }
}
