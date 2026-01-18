package momzzangseven.mztkbe.modules.level.application.port.in;

import momzzangseven.mztkbe.modules.level.application.dto.RecordUserActionCompletionCommand;

/** Use case for recording a user action completion (certification). */
public interface RecordUserActionCompletionUseCase {
  void execute(RecordUserActionCompletionCommand command);
}
