package momzzangseven.mztkbe.modules.image.application.port.in;

import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferencesCommand;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferencesResult;

public interface GetImagesByReferencesUseCase {

  GetImagesByReferencesResult execute(GetImagesByReferencesCommand command);
}
