package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;

public interface ManageQnaAnswerExecutionIntentRefPort {

  void upsert(QnaAnswerExecutionIntentRef ref);

  Optional<QnaAnswerExecutionIntentRef> findByExecutionIntentId(String executionIntentId);

  List<QnaAnswerExecutionIntentRef> findByPostIdAndActionType(
      Long postId, QnaExecutionActionType actionType);

  record QnaAnswerExecutionIntentRef(
      String executionIntentId,
      Long postId,
      Long answerId,
      QnaExecutionActionType actionType,
      String statusSnapshot) {}
}
