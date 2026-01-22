package momzzangseven.mztkbe.modules.level.application.port.in;

import momzzangseven.mztkbe.modules.level.application.dto.LevelUpCommand;
import momzzangseven.mztkbe.modules.level.application.dto.LevelUpResult;

public interface LevelUpUseCase {
  LevelUpResult execute(LevelUpCommand command);
}
