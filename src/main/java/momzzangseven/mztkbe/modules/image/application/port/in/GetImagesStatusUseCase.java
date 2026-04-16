package momzzangseven.mztkbe.modules.image.application.port.in;

import momzzangseven.mztkbe.modules.image.application.dto.GetImagesStatusCommand;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesStatusResult;

public interface GetImagesStatusUseCase {
  GetImagesStatusResult execute(GetImagesStatusCommand command);
}
