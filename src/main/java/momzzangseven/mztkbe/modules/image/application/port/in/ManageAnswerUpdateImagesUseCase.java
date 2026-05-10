package momzzangseven.mztkbe.modules.image.application.port.in;

import momzzangseven.mztkbe.modules.image.application.dto.ApplyAnswerUpdateImagesCommand;
import momzzangseven.mztkbe.modules.image.application.dto.ReleaseAnswerUpdateImagesCommand;
import momzzangseven.mztkbe.modules.image.application.dto.ReserveAnswerUpdateImagesCommand;

public interface ManageAnswerUpdateImagesUseCase {

  void reservePendingImages(ReserveAnswerUpdateImagesCommand command);

  void applyPendingImages(ApplyAnswerUpdateImagesCommand command);

  void releasePendingImages(ReleaseAnswerUpdateImagesCommand command);
}
