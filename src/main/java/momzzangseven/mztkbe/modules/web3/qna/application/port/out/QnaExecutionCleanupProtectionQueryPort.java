package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.ManageQnaAnswerExecutionIntentRefPort.QnaAnswerExecutionIntentRef;

public interface QnaExecutionCleanupProtectionQueryPort {

  Optional<QnaAnswerExecutionIntentRef> findAnswerExecutionIntentRef(String executionIntentId);

  boolean hasProtectedAnswerUpdateState(String executionIntentId);
}
