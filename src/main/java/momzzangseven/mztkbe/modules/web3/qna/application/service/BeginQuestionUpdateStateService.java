package momzzangseven.mztkbe.modules.web3.qna.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.RetryableWeb3PreparationException;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.BeginQuestionUpdateStateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QuestionUpdateStatePreparationResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.BeginQuestionUpdateStateUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaExecutionIntentStatePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaQuestionUpdateStatePersistencePort;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaQuestionUpdateState;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;

@RequiredArgsConstructor
public class BeginQuestionUpdateStateService implements BeginQuestionUpdateStateUseCase {

  private final QnaQuestionUpdateStatePersistencePort statePersistencePort;
  private final LoadQnaExecutionIntentStatePort loadQnaExecutionIntentStatePort;
  private final Clock appClock;

  @Override
  public QuestionUpdateStatePreparationResult begin(BeginQuestionUpdateStateCommand command) {
    command.validate();
    LocalDateTime now = LocalDateTime.now(appClock);
    QnaQuestionUpdateState latest =
        statePersistencePort.findLatestByPostIdForUpdate(command.postId()).orElse(null);
    if (loadQnaExecutionIntentStatePort.hasConflictingActiveIntent(
        QnaExecutionResourceType.QUESTION,
        String.valueOf(command.postId()),
        QnaExecutionActionType.QNA_QUESTION_UPDATE)) {
      throw new RetryableWeb3PreparationException(
          "question has pending onchain mutation; wait for completion or recover first");
    }

    Long nextVersion = latest == null ? 1L : latest.getUpdateVersion() + 1;
    statePersistencePort.markSupersedableStaleByPostId(command.postId());
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
