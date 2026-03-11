package momzzangseven.mztkbe.modules.image.infrastructure.s3;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.image.application.port.out.GeneratePresignedUrlPort;
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

  private final S3Presigner s3Presigner;

  @Value("${cloud.aws.s3.bucket}")
  private String bucket;

  @Value("${cloud.aws.s3.presigned-url.expiry-seconds}")
  private long expirySeconds;

  @Override
  public String generatePutPresignedUrl(String objectKey, String contentType) {
    PutObjectRequest putRequest =
        PutObjectRequest.builder().bucket(bucket).key(objectKey).contentType(contentType).build();

    PutObjectPresignRequest presignRequest =
        PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofSeconds(expirySeconds))
            .putObjectRequest(putRequest)
            .build();

    PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
    return presigned.url().toString();
  }
}
