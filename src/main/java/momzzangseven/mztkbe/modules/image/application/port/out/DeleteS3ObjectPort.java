package momzzangseven.mztkbe.modules.image.application.port.out;

/**
 * Hexagonal Architecture: OUTPUT PORT. Abstraction for deleting objects from S3-compatible storage.
 * Implemented by {@code S3ObjectDeleteAdapter} in the infrastructure layer.
 */
public interface DeleteS3ObjectPort {

  /**
   * Deletes an object from S3. This is a best-effort operation. Implementations should log warnings
   * on failure but must not throw exceptions so that the calling transaction can continue.
   *
   * @param objectKey the S3 object key to delete
   */
  void deleteObject(String objectKey);
}
