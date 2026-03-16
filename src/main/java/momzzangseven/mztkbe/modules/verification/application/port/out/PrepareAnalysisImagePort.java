package momzzangseven.mztkbe.modules.verification.application.port.out;

import java.io.IOException;
import java.nio.file.Path;
import momzzangseven.mztkbe.modules.verification.application.dto.PreparedAnalysisImage;

public interface PrepareAnalysisImagePort {
  PreparedAnalysisImage prepare(Path originalPath, int maxLongEdge, double webpQuality)
      throws IOException;
}
