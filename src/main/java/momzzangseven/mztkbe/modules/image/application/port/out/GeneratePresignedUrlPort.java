package momzzangseven.mztkbe.modules.image.application.port.out;

/**
 * Hexagonal Architecture: OUTPUT PORT. Abstraction for generating S3 pre-signed PUT URLs.
 * Implemented by S3PresignedUrlAdapter in the infrastructure layer.
 */
public interface GeneratePresignedUrlPort {

  /**
   * Generates a pre-signed PUT URL for the given S3 object key.
   *
   * @param objectKey the full S3 object key (e.g. "public/community/free/tmp/{uuid}.jpg")
   * @param contentType MIME type of the file (e.g. "image/jpeg")
   * @return a time-limited presigned URL string
   */
  String generatePutPresignedUrl(String objectKey, String contentType);
}
