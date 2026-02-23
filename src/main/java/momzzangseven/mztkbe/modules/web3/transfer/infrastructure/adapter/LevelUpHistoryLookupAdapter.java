package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.level.application.port.in.CheckLevelUpHistoryExistsUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.CheckLevelUpHistoryExistsPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LevelUpHistoryLookupAdapter implements CheckLevelUpHistoryExistsPort {

  private final CheckLevelUpHistoryExistsUseCase checkLevelUpHistoryExistsUseCase;

  @Override
  public boolean existsById(Long levelUpHistoryId) {
    return checkLevelUpHistoryExistsUseCase.execute(levelUpHistoryId);
  }
}
