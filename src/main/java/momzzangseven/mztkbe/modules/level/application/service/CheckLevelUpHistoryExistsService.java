package momzzangseven.mztkbe.modules.level.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.level.application.port.in.CheckLevelUpHistoryExistsUseCase;
import momzzangseven.mztkbe.modules.level.application.port.out.LevelUpHistoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CheckLevelUpHistoryExistsService implements CheckLevelUpHistoryExistsUseCase {

  private final LevelUpHistoryPort levelUpHistoryPort;

  @Override
  @Transactional(readOnly = true)
  public boolean execute(Long levelUpHistoryId) {
    return levelUpHistoryPort.existsById(levelUpHistoryId);
  }
}
