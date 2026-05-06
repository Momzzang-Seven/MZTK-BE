package momzzangseven.mztkbe.modules.web3.qna.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.BeginQuestionUpdateStateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QuestionUpdateStatePreparationResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.BeginQuestionUpdateStateUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaQuestionUpdateStatePersistencePort;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaQuestionUpdateState;

@RequiredArgsConstructor
public class BeginQuestionUpdateStateService implements BeginQuestionUpdateStateUseCase {

  private final QnaQuestionUpdateStatePersistencePort statePersistencePort;
  private final Clock appClock;

  @Override
  public QuestionUpdateStatePreparationResult begin(BeginQuestionUpdateStateCommand command) {
    command.validate();
    LocalDateTime now = LocalDateTime.now(appClock);
    Long nextVersion =
        statePersistencePort
            .findLatestByPostIdForUpdate(command.postId())
            .map(QnaQuestionUpdateState::getUpdateVersion)
            .map(version -> version + 1)
            .orElse(1L);

    statePersistencePort.markNonTerminalStaleByPostId(command.postId());
    QnaQuestionUpdateState saved =
        statePersistencePort.save(
            QnaQuestionUpdateState.preparing(
                command.postId(),
                command.requesterUserId(),
                nextVersion,
                UUID.randomUUID().toString(),
                command.expectedQuestionHash(),
                now));

    return new QuestionUpdateStatePreparationResult(
        saved.getPostId(),
        saved.getUpdateVersion(),
        saved.getUpdateToken(),
        saved.getExpectedQuestionHash());
  }
}
