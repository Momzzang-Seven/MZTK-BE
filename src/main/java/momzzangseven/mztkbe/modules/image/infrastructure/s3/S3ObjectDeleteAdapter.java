package momzzangseven.mztkbe.modules.image.infrastructure.s3;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.image.application.port.out.DeleteS3ObjectPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3ObjectDeleteAdapter implements DeleteS3ObjectPort {
  private final S3Client s3Client;

  @Value("${cloud.aws.s3.bucket}")
  private String bucketName;

  @Override
  public void deleteObject(String objectKey) {
    try {
      s3Client.deleteObject(
          DeleteObjectRequest.builder().bucket(bucketName).key(objectKey).build());
    } catch (Exception e) {
      log.warn("Best-effort S3 delete failed for key={}: {}", objectKey, e.getMessage());
    }
  }
}
