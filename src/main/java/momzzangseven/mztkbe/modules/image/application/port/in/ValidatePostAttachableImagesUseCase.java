package momzzangseven.mztkbe.modules.image.application.port.in;

import momzzangseven.mztkbe.modules.image.application.dto.ValidatePostAttachableImagesCommand;

public interface ValidatePostAttachableImagesUseCase {
  void execute(ValidatePostAttachableImagesCommand command);
}
