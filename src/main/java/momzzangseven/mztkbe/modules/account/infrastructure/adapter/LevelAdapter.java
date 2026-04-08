package momzzangseven.mztkbe.modules.account.infrastructure.adapter;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.account.application.port.out.DeleteUserLevelDataPort;
import momzzangseven.mztkbe.modules.level.application.dto.DeleteUserLevelDataCommand;
import momzzangseven.mztkbe.modules.level.application.port.in.DeleteUserLevelDataUseCase;
import org.springframework.stereotype.Component;

/**
 * Infrastructure adapter bridging account's output port to level module's inbound port for deleting
 * user level data during hard-delete.
 */
@Component
@RequiredArgsConstructor
public class LevelAdapter implements DeleteUserLevelDataPort {

  private final DeleteUserLevelDataUseCase deleteUserLevelDataUseCase;

  @Override
  public void deleteByUserIds(List<Long> userIds) {
    deleteUserLevelDataUseCase.execute(new DeleteUserLevelDataCommand(userIds));
  }
}
