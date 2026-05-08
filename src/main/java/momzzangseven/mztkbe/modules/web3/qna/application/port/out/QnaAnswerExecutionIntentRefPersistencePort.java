package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

import java.util.List;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;

public interface QnaAnswerExecutionIntentRefPersistencePort {

  void upsert(QnaAnswerExecutionIntentRef ref);

  List<QnaAnswerExecutionIntentRef> findByPostIdAndActionType(
      Long postId, QnaExecutionActionType actionType);

  record QnaAnswerExecutionIntentRef(
      String executionIntentId,
      Long postId,
      Long answerId,
      QnaExecutionActionType actionType,
      String statusSnapshot) {}
}
