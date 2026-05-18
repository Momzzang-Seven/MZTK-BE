package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionResumeView;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetLatestExecutionIntentSummariesQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetLatestExecutionIntentSummaryResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetLatestExecutionIntentSummaryUseCase;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionResourceTypeCode;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationExecutionResumeAdapterTest {

  @Mock private GetLatestExecutionIntentSummaryUseCase getLatestExecutionIntentSummaryUseCase;

  @Test
  void loadLatestBatch_emptyInput_returnsEmptyWithoutDelegateCall() {
    ReservationExecutionResumeAdapter sut =
        new ReservationExecutionResumeAdapter(getLatestExecutionIntentSummaryUseCase);

    assertThat(sut.loadLatestBatch(List.of())).isEmpty();
    assertThat(sut.loadLatestBatch(null)).isEmpty();
    then(getLatestExecutionIntentSummaryUseCase).shouldHaveNoInteractions();
  }

  @Test
  void loadLatestBatch_mapsOrderResourceStringKeysToLongKeysAndTransactionSummary() {
    ReservationExecutionResumeAdapter sut =
        new ReservationExecutionResumeAdapter(getLatestExecutionIntentSummaryUseCase);
    Map<String, GetLatestExecutionIntentSummaryResult> summaries = new LinkedHashMap<>();
    summaries.put(
        "10", summary("10", "intent-10", 100L, ExecutionTransactionStatus.SUCCEEDED, "0xtx10"));
    summaries.put("11", summary("11", "intent-11", null, null, null));
    given(getLatestExecutionIntentSummaryUseCase.executeBatch(org.mockito.ArgumentMatchers.any()))
        .willReturn(summaries);

    Map<Long, ReservationExecutionResumeView> result = sut.loadLatestBatch(List.of(10L, 11L));

    assertThat(result).containsOnlyKeys(10L, 11L);
    assertThat(result.get(10L).resource().type()).isEqualTo("ORDER");
    assertThat(result.get(10L).resource().id()).isEqualTo("10");
    assertThat(result.get(10L).actionType()).isEqualTo("MARKETPLACE_CLASS_PURCHASE");
    assertThat(result.get(10L).executionIntent().id()).isEqualTo("intent-10");
    assertThat(result.get(10L).transaction().id()).isEqualTo(100L);
    assertThat(result.get(10L).transaction().status()).isEqualTo("SUCCEEDED");
    assertThat(result.get(10L).transaction().txHash()).isEqualTo("0xtx10");
    assertThat(result.get(11L).transaction()).isNull();
    ArgumentCaptor<GetLatestExecutionIntentSummariesQuery> queryCaptor =
        ArgumentCaptor.forClass(GetLatestExecutionIntentSummariesQuery.class);
    then(getLatestExecutionIntentSummaryUseCase).should().executeBatch(queryCaptor.capture());
    assertThat(queryCaptor.getValue().resourceType()).isEqualTo(ExecutionResourceTypeCode.ORDER);
    assertThat(queryCaptor.getValue().resourceIds()).containsExactly("10", "11");
  }

  private GetLatestExecutionIntentSummaryResult summary(
      String resourceId,
      String intentId,
      Long transactionId,
      ExecutionTransactionStatus transactionStatus,
      String txHash) {
    return new GetLatestExecutionIntentSummaryResult(
        ExecutionResourceType.ORDER,
        resourceId,
        ExecutionResourceStatus.PENDING_EXECUTION,
        ExecutionActionType.MARKETPLACE_CLASS_PURCHASE,
        intentId,
        ExecutionIntentStatus.AWAITING_SIGNATURE,
        LocalDateTime.of(2026, 5, 18, 10, 0),
        1_800L,
        ExecutionMode.EIP7702,
        2,
        transactionId,
        transactionStatus,
        txHash);
  }
}
