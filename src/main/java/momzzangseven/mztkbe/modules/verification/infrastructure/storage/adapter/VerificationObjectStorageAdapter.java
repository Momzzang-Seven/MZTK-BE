package momzzangseven.mztkbe.modules.verification.infrastructure.storage.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.verification.application.port.out.ObjectStoragePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

@Component
@RequiredArgsConstructor
public class VerificationObjectStorageAdapter implements ObjectStoragePort {

  private final S3Client s3Client;

  @Value("${cloud.aws.s3.bucket}")
  private String bucket;

  @Override
  public boolean exists(String objectKey) {
    try {
      s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(objectKey).build());
      return true;
    } catch (NoSuchKeyException ex) {
      return false;
    }
  }

  @Override
  public byte[] readBytes(String objectKey) {
    ResponseBytes<GetObjectResponse> bytes =
        s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(objectKey).build());
    return bytes.asByteArray();
  }
}
