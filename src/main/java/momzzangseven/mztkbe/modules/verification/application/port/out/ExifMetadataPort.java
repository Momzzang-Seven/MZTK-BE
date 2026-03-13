package momzzangseven.mztkbe.modules.verification.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.verification.application.dto.ExifMetadataInfo;

public interface ExifMetadataPort {
  Optional<ExifMetadataInfo> extract(byte[] bytes);
}
