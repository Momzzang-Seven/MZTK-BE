package momzzangseven.mztkbe.modules.verification.application.port.out;

import java.io.IOException;
import momzzangseven.mztkbe.modules.verification.application.dto.PreparedAnalysisImage;

public interface PrepareAnalysisImagePort {
  PreparedAnalysisImage prepare(byte[] bytes, String extension, int maxLongEdge, double webpQuality)
      throws IOException;
}
