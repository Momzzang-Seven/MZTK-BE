package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

import java.util.List;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;

public interface LoadQnaExecutionCleanupIntentPort {

  List<QnaExecutionCleanupIntent> loadByIds(List<Long> intentIds);

  record QnaExecutionCleanupIntent(
      Long id,
      String executionIntentId,
      QnaExecutionResourceType resourceType,
      String resourceId,
      QnaExecutionActionType actionType,
      Long requesterUserId) {}
}
