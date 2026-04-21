package momzzangseven.mztkbe.modules.image.application.port.out;

/** Output port for image storage URL resolution. */
public interface ImageStoragePort {

  /** Returns the full image URL for the given S3 object key, or null if the key is null/blank. */
  String buildImageUrl(String objectKey);
}
