package momzzangseven.mztkbe.modules.web3.qna.application.service;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.FilterQnaExecutionCleanupCandidatesUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.CheckQnaAnswerCleanupProtectionPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.CheckQnaQuestionCleanupProtectionPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaExecutionCleanupIntentPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaExecutionCleanupIntentPort.QnaExecutionCleanupIntent;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.ManageQnaAnswerExecutionIntentRefPort.QnaAnswerExecutionIntentRef;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaExecutionCleanupProtectionQueryPort;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "eip7702.cleanup.enabled"},
    havingValue = "true")
public class QnaExecutionCleanupProtectionService
    implements FilterQnaExecutionCleanupCandidatesUseCase {

  private final LoadQnaExecutionCleanupIntentPort loadQnaExecutionCleanupIntentPort;
  private final CheckQnaQuestionCleanupProtectionPort checkQnaQuestionCleanupProtectionPort;
  private final CheckQnaAnswerCleanupProtectionPort checkQnaAnswerCleanupProtectionPort;
  private final QnaExecutionCleanupProtectionQueryPort qnaExecutionCleanupProtectionQueryPort;

  @Override
  @Transactional(readOnly = true)
  public List<Long> filterDeletableFinalizedIntentIds(List<Long> candidateIntentIds) {
    if (candidateIntentIds == null || candidateIntentIds.isEmpty()) {
      return List.of();
    }
    return loadQnaExecutionCleanupIntentPort.loadByIds(candidateIntentIds).stream()
        .filter(intent -> !isProtected(intent))
        .map(QnaExecutionCleanupIntent::id)
        .toList();
  }

  private boolean isProtected(QnaExecutionCleanupIntent intent) {
    if (intent.resourceType() == QnaExecutionResourceType.QUESTION
        && intent.actionType() == QnaExecutionActionType.QNA_QUESTION_CREATE) {
      return parseLong(intent.resourceId())
          .map(
              postId ->
                  checkQnaQuestionCleanupProtectionPort.hasFailedQuestionCreate(
                      postId, intent.requesterUserId()))
          .orElse(false);
    }
    if (intent.resourceType() != QnaExecutionResourceType.ANSWER || !isAnswerAction(intent)) {
      return false;
    }
    if (checkQnaAnswerCleanupProtectionPort.hasCurrentCreateIntent(intent.executionIntentId())) {
      return true;
    }
    if (checkQnaAnswerCleanupProtectionPort.hasCurrentDeleteIntent(intent.executionIntentId())) {
      return true;
    }
    if (qnaExecutionCleanupProtectionQueryPort.hasProtectedAnswerUpdateState(
        intent.executionIntentId())) {
      return true;
    }
    return qnaExecutionCleanupProtectionQueryPort
        .findAnswerExecutionIntentRef(intent.executionIntentId())
        .filter(ref -> ref.actionType() == QnaExecutionActionType.QNA_ANSWER_SUBMIT)
        .map(QnaAnswerExecutionIntentRef::answerId)
        .map(checkQnaAnswerCleanupProtectionPort::hasFailedAnswer)
        .orElse(false);
  }

  private boolean isAnswerAction(QnaExecutionCleanupIntent intent) {
    return intent.actionType() == QnaExecutionActionType.QNA_ANSWER_SUBMIT
        || intent.actionType() == QnaExecutionActionType.QNA_ANSWER_UPDATE
        || intent.actionType() == QnaExecutionActionType.QNA_ANSWER_DELETE;
  }

  private Optional<Long> parseLong(String value) {
    try {
      return value == null || value.isBlank() ? Optional.empty() : Optional.of(Long.valueOf(value));
    } catch (NumberFormatException ignored) {
      return Optional.empty();
    }
  }
}
