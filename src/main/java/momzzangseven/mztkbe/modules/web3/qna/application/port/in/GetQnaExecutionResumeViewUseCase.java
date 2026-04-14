package momzzangseven.mztkbe.modules.web3.qna.application.port.in;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.GetQnaExecutionResumeBatchViewQuery;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.GetQnaExecutionResumeViewQuery;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionResumeViewResult;

public interface GetQnaExecutionResumeViewUseCase {

  Optional<QnaExecutionResumeViewResult> execute(GetQnaExecutionResumeViewQuery query);

  default Map<Long, QnaExecutionResumeViewResult> executeBatch(
      GetQnaExecutionResumeBatchViewQuery query) {
    Map<Long, QnaExecutionResumeViewResult> results = new LinkedHashMap<>();
    for (Long resourceId : query.resourceIds()) {
      execute(new GetQnaExecutionResumeViewQuery(query.resourceType(), resourceId))
          .ifPresent(result -> results.put(resourceId, result));
    }
    return results;
  }
}
