package momzzangseven.mztkbe.modules.image.application.port.out;

import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;

/**
 * Hexagonal Architecture: OUTPUT PORT. Abstraction for generating S3 pre-signed PUT URLs.
 * Implemented by S3PresignedUrlAdapter in the infrastructure layer.
 */
public interface GeneratePresignedUrlPort {

  /**
   * Generate a pre-signed PUT URL for the given information
   *
   * @param referenceType reference type
   * @param uuid uuid
   * @param extension extension
   * @return PresignedUrlWithKey
   */
  PresignedUrlWithKey generatePutPresignedUrl(
      ImageReferenceType referenceType, String uuid, String extension);
}
