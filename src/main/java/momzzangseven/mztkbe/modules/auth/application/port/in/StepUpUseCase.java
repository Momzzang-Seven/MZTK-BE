package momzzangseven.mztkbe.modules.auth.application.port.in;

import momzzangseven.mztkbe.modules.auth.application.dto.StepUpCommand;
import momzzangseven.mztkbe.modules.auth.application.dto.StepUpResult;

/** Use case for step-up authentication (re-authentication for sensitive actions). */
public interface StepUpUseCase {
  StepUpResult execute(StepUpCommand command);
}
