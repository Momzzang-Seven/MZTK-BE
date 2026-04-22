package momzzangseven.mztkbe.modules.web3.qna.application.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetLatestExecutionIntentSummariesQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetLatestExecutionIntentSummaryQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetLatestExecutionIntentSummaryUseCase;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionResourceTypeCode;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.GetQnaExecutionResumeBatchViewQuery;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.GetQnaExecutionResumeViewQuery;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionResumeViewResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.GetQnaExecutionResumeViewUseCase;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetQnaExecutionResumeViewService implements GetQnaExecutionResumeViewUseCase {

  private final GetLatestExecutionIntentSummaryUseCase getLatestExecutionIntentSummaryUseCase;

  @Override
  public Optional<QnaExecutionResumeViewResult> execute(GetQnaExecutionResumeViewQuery query) {
    return getLatestExecutionIntentSummaryUseCase
        .execute(
            new GetLatestExecutionIntentSummaryQuery(
                ExecutionResourceTypeCode.valueOf(query.resourceType().name()),
                String.valueOf(query.resourceId())))
        .map(summary -> QnaExecutionResumeViewMapper.toResult(query.resourceType(), summary));
  }

  @Override
  public Map<Long, QnaExecutionResumeViewResult> executeBatch(
      GetQnaExecutionResumeBatchViewQuery query) {
    Map<Long, QnaExecutionResumeViewResult> results = new LinkedHashMap<>();
    getLatestExecutionIntentSummaryUseCase
        .executeBatch(
            new GetLatestExecutionIntentSummariesQuery(
                ExecutionResourceTypeCode.valueOf(query.resourceType().name()),
                query.resourceIds().stream().map(String::valueOf).toList()))
        .forEach(
            (resourceId, summary) ->
                results.put(
                    Long.parseLong(resourceId),
                    QnaExecutionResumeViewMapper.toResult(query.resourceType(), summary)));
    return results;
  }
}
