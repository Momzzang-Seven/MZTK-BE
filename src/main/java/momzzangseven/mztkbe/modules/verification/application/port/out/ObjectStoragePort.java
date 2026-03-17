package momzzangseven.mztkbe.modules.verification.application.port.out;

import java.io.IOException;
import momzzangseven.mztkbe.modules.verification.application.dto.StorageObjectStream;

public interface ObjectStoragePort {
  boolean exists(String objectKey);

  StorageObjectStream openStream(String objectKey) throws IOException;
}
