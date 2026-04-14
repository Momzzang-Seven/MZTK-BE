package momzzangseven.mztkbe.modules.web3.qna.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetLatestExecutionIntentSummaryQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetLatestExecutionIntentSummaryResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetLatestExecutionIntentSummaryUseCase;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.GetQnaExecutionResumeViewQuery;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionResumeViewResult;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetQnaExecutionResumeViewServiceTest {

  @Mock private GetLatestExecutionIntentSummaryUseCase getLatestExecutionIntentSummaryUseCase;

  private GetQnaExecutionResumeViewService service;

  @BeforeEach
  void setUp() {
    service = new GetQnaExecutionResumeViewService(getLatestExecutionIntentSummaryUseCase);
  }

  @Test
  void execute_mapsSummaryToQnaResumeView() {
    when(getLatestExecutionIntentSummaryUseCase.execute(
            new GetLatestExecutionIntentSummaryQuery(
                momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionResourceTypeCode
                    .QUESTION,
                "101")))
        .thenReturn(
            Optional.of(
                new GetLatestExecutionIntentSummaryResult(
                    ExecutionResourceType.QUESTION,
                    "101",
                    ExecutionResourceStatus.COMPLETED,
                    "intent-101",
                    ExecutionIntentStatus.CONFIRMED,
                    LocalDateTime.of(2026, 4, 11, 12, 30),
                    ExecutionMode.EIP7702,
                    2,
                    501L,
                    ExecutionTransactionStatus.SUCCEEDED,
                    "0xabc501")));

    Optional<QnaExecutionResumeViewResult> result =
        service.execute(
            new GetQnaExecutionResumeViewQuery(QnaExecutionResourceType.QUESTION, 101L));

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().resource().type()).isEqualTo(QnaExecutionResourceType.QUESTION);
    assertThat(result.orElseThrow().resource().id()).isEqualTo("101");
    assertThat(result.orElseThrow().resource().status())
        .isEqualTo(QnaExecutionResourceStatus.COMPLETED);
    assertThat(result.orElseThrow().executionIntent().id()).isEqualTo("intent-101");
    assertThat(result.orElseThrow().executionIntent().status()).isEqualTo("CONFIRMED");
    assertThat(result.orElseThrow().execution().mode()).isEqualTo("EIP7702");
    assertThat(result.orElseThrow().execution().signCount()).isEqualTo(2);
    assertThat(result.orElseThrow().transaction()).isNotNull();
    assertThat(result.orElseThrow().transaction().id()).isEqualTo(501L);
    assertThat(result.orElseThrow().transaction().status()).isEqualTo("SUCCEEDED");
    assertThat(result.orElseThrow().transaction().txHash()).isEqualTo("0xabc501");
  }

  @Test
  void execute_returnsEmptyWhenLatestSummaryIsMissing() {
    when(getLatestExecutionIntentSummaryUseCase.execute(
            new GetLatestExecutionIntentSummaryQuery(
                momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionResourceTypeCode
                    .ANSWER,
                "201")))
        .thenReturn(Optional.empty());

    Optional<QnaExecutionResumeViewResult> result =
        service.execute(new GetQnaExecutionResumeViewQuery(QnaExecutionResourceType.ANSWER, 201L));

    assertThat(result).isEmpty();
  }
}
