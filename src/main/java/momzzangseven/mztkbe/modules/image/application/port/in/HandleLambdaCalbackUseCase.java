package momzzangseven.mztkbe.modules.image.application.port.in;

import momzzangseven.mztkbe.modules.image.application.dto.LambdaCallbackCommand;

/**
 * Input port for processing the Lambda image-processing webhook callback. Validates the callback
 * and transitions the image status.
 */
public interface HandleLambdaCalbackUseCase {
  void execute(LambdaCallbackCommand command);
}
