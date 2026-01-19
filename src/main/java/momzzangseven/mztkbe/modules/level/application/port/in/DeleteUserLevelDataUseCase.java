package momzzangseven.mztkbe.modules.level.application.port.in;

import momzzangseven.mztkbe.modules.level.application.dto.DeleteUserLevelDataCommand;

/** Deletes all level-related user data (used for hard delete). */
public interface DeleteUserLevelDataUseCase {
  void execute(DeleteUserLevelDataCommand command);
}

