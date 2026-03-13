package momzzangseven.mztkbe.modules.verification.application.port.out;

public interface ObjectStoragePort {
  boolean exists(String objectKey);

  byte[] readBytes(String objectKey);
}
