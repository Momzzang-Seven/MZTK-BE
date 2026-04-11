package momzzangseven.mztkbe.modules.web3.qna.application.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetLatestExecutionIntentSummaryQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetLatestExecutionIntentSummaryUseCase;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionResourceTypeCode;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.GetQnaExecutionResumeViewQuery;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionResumeViewResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.GetQnaExecutionResumeViewUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
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
}
