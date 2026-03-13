package momzzangseven.mztkbe.modules.image.infrastructure.s3;

import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.image.InvalidImageExtensionException;
import momzzangseven.mztkbe.global.error.image.InvalidImageRefTypeException;
import momzzangseven.mztkbe.modules.image.application.port.out.GeneratePresignedUrlPort;
import momzzangseven.mztkbe.modules.image.application.port.out.PresignedUrlWithKey;
import momzzangseven.mztkbe.modules.image.domain.vo.AllowedImageExtension;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Implements GeneratePresignedUrlPort using AWS SDK v2 S3Presigner. Generates time-limited PUT
 * presigned URLs for direct browser-to-S3 uploads.
 */
@Component
@RequiredArgsConstructor
public class S3PresignedUrlAdapter implements GeneratePresignedUrlPort {

  private static final Map<ImageReferenceType, String> PATH_PREFIX_MAP =
      Map.of(
          ImageReferenceType.COMMUNITY_FREE, "public/community/free/tmp/",
          ImageReferenceType.COMMUNITY_QUESTION, "public/community/question/tmp/",
          ImageReferenceType.COMMUNITY_ANSWER, "public/community/answer/tmp/",
          ImageReferenceType.MARKET_THUMB, "public/market/thumb/tmp/",
          ImageReferenceType.MARKET_DETAIL, "public/market/detail/tmp/",
          ImageReferenceType.WORKOUT, "private/workout/");

  private final S3Presigner s3Presigner;

  @Value("${cloud.aws.s3.bucket}")
  private String bucket;

  @Value("${cloud.aws.s3.presigned-url.expiry-seconds}")
  private long expirySeconds;

  @Override
  public PresignedUrlWithKey generatePutPresignedUrl(
      ImageReferenceType referenceType, String uuid, String extension) {

    String prefix = PATH_PREFIX_MAP.get(referenceType);
    if (prefix == null) {
      throw new InvalidImageRefTypeException(referenceType + " has no S3 path prefix configured.");
    }
    String objectKey = prefix + uuid + "." + extension;

    String contentType = resolveContentType(extension);

    PutObjectRequest putRequest =
        PutObjectRequest.builder().bucket(bucket).key(objectKey).contentType(contentType).build();

    PutObjectPresignRequest presignRequest =
        PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofSeconds(expirySeconds))
            .putObjectRequest(putRequest)
            .build();

    PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);

    return new PresignedUrlWithKey(presigned.url().toString(), objectKey);
  }

  // ================================================
  // HELPER METHOD
  // ================================================

  /**
   * Resolves the MIME content type from a filename extension.
   *
   * @param extension file extension
   * @return MIME type string
   */
  private String resolveContentType(String extension) {
    if (!AllowedImageExtension.isAllowedWithExtension(extension)) {
      throw new InvalidImageExtensionException("Unsupported image extension: " + extension + ".");
    }
    return switch (extension) {
      case "png" -> "image/png";
      case "gif" -> "image/gif";
      case "heif" -> "image/heif";
      case "heic" -> "image/heic";
      default -> "image/jpeg";
    };
  }
}
