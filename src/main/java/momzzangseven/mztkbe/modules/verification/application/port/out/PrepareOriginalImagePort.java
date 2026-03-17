package momzzangseven.mztkbe.modules.verification.application.port.out;

import java.io.IOException;
import momzzangseven.mztkbe.modules.verification.application.dto.PreparedOriginalImage;

public interface PrepareOriginalImagePort {
  PreparedOriginalImage prepare(String objectKey, String extension) throws IOException;
}
