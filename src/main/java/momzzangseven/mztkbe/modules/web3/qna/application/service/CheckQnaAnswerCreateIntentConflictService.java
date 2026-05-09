package momzzangseven.mztkbe.modules.web3.qna.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.CheckQnaAnswerCreateIntentConflictUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaExecutionIntentStatePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.ManageQnaAnswerExecutionIntentRefPort;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CheckQnaAnswerCreateIntentConflictService
    implements CheckQnaAnswerCreateIntentConflictUseCase {

  private final ManageQnaAnswerExecutionIntentRefPort refPersistencePort;
  private final LoadQnaExecutionIntentStatePort loadQnaExecutionIntentStatePort;

  @Override
  @Transactional(readOnly = true)
  public boolean hasActiveAnswerCreateIntent(Long postId) {
    if (postId == null) {
      return false;
    }
    return refPersistencePort
        .findByPostIdAndActionType(postId, QnaExecutionActionType.QNA_ANSWER_SUBMIT)
        .stream()
        .anyMatch(
            ref ->
                loadQnaExecutionIntentStatePort
                    .loadByExecutionIntentId(ref.executionIntentId())
                    .map(state -> state.status() != null && state.status().isActive())
                    .orElse(false));
  }
}
