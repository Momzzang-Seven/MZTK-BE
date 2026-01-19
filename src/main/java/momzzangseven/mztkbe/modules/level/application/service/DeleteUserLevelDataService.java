package momzzangseven.mztkbe.modules.level.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.level.application.dto.DeleteUserLevelDataCommand;
import momzzangseven.mztkbe.modules.level.application.port.in.DeleteUserLevelDataUseCase;
import momzzangseven.mztkbe.modules.level.application.port.out.LevelRetentionPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class DeleteUserLevelDataService implements DeleteUserLevelDataUseCase {

  private final LevelRetentionPort levelRetentionPort;

  @Override
  public void execute(DeleteUserLevelDataCommand command) {
    if (command == null) {
      throw new IllegalArgumentException("command is required");
    }
    command.validate();

    levelRetentionPort.deleteUserLevelDataByUserIds(command.userIds());
    log.info("Deleted user level data: userCount={}", command.userIds().size());
  }
}

