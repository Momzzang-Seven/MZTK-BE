package momzzangseven.mztkbe.modules.verification.application.port.out;

/** Outbound port for loading objects already uploaded through the presigned-url image flow. */
public interface ObjectStoragePort {

  StoredObject load(String objectKey);

  boolean exists(String objectKey);

  void delete(String objectKey);

  /** Uploaded verification object fetched from storage for synchronous analysis. */
  record StoredObject(String objectKey, String contentType, long sizeBytes, byte[] bytes) {}
}
