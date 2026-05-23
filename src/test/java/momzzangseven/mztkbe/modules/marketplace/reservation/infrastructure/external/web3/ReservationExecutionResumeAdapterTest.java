package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetLatestExecutionIntentSummariesQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetLatestExecutionIntentSummaryQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetLatestExecutionIntentSummaryResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetLatestExecutionIntentSummaryUseCase;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionResourceTypeCode;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReservationExecutionResumeAdapter 테스트")
class ReservationExecutionResumeAdapterTest {

  @Mock private GetLatestExecutionIntentSummaryUseCase getLatestExecutionIntentSummaryUseCase;

  private ReservationExecutionResumeAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new ReservationExecutionResumeAdapter(getLatestExecutionIntentSummaryUseCase);
  }

  @Test
  @DisplayName("단건 resume summary를 reservation read model로 매핑한다")
  void loadLatest_mapsSummaryWithTransaction() {
    when(getLatestExecutionIntentSummaryUseCase.execute(
            new GetLatestExecutionIntentSummaryQuery(ExecutionResourceTypeCode.ORDER, "10")))
        .thenReturn(Optional.of(summary("10", 77L, ExecutionTransactionStatus.SUCCEEDED)));

    var result = adapter.loadLatest(10L);

    assertThat(result).isPresent();
    var view = result.get();
    assertThat(view.resource().type()).isEqualTo("ORDER");
    assertThat(view.resource().id()).isEqualTo("10");
    assertThat(view.resource().status()).isEqualTo("PENDING_EXECUTION");
    assertThat(view.actionType()).isEqualTo("MARKETPLACE_CLASS_PURCHASE");
    assertThat(view.executionIntent().id()).isEqualTo("intent-10");
    assertThat(view.executionIntent().status()).isEqualTo("PENDING_ONCHAIN");
    assertThat(view.executionIntent().expiresAtEpochSeconds()).isEqualTo(1_768_224_000L);
    assertThat(view.execution().mode()).isEqualTo("EIP7702");
    assertThat(view.execution().signCount()).isEqualTo(1);
    assertThat(view.transaction().id()).isEqualTo(77L);
    assertThat(view.transaction().status()).isEqualTo("SUCCEEDED");
    assertThat(view.transaction().txHash()).isEqualTo("0xtx10");
  }

  @Test
  @DisplayName("batch resume summary는 String resource id를 Long key로 변환하고 transaction null을 보존한다")
  void loadLatestBatch_mapsLongKeysAndNullableTransaction() {
    when(getLatestExecutionIntentSummaryUseCase.executeBatch(
            new GetLatestExecutionIntentSummariesQuery(
                ExecutionResourceTypeCode.ORDER, java.util.List.of("10", "20"))))
        .thenReturn(
            Map.of(
                "10", summary("10", 77L, ExecutionTransactionStatus.PENDING),
                "20", summary("20", null, null)));

    var results = adapter.loadLatestBatch(java.util.List.of(10L, 20L));

    assertThat(results).containsOnlyKeys(10L, 20L);
    assertThat(results.get(10L).transaction().id()).isEqualTo(77L);
    assertThat(results.get(10L).transaction().status()).isEqualTo("PENDING");
    assertThat(results.get(20L).transaction()).isNull();
  }

  @Test
  @DisplayName("batch input이 비어 있으면 shared execution을 호출하지 않는다")
  void loadLatestBatch_emptyInputReturnsEmptyMap() {
    assertThat(adapter.loadLatestBatch(java.util.List.of())).isEmpty();

    verify(getLatestExecutionIntentSummaryUseCase, never())
        .executeBatch(any(GetLatestExecutionIntentSummariesQuery.class));
  }

  private static GetLatestExecutionIntentSummaryResult summary(
      String resourceId, Long transactionId, ExecutionTransactionStatus transactionStatus) {
    return new GetLatestExecutionIntentSummaryResult(
        ExecutionResourceType.ORDER,
        resourceId,
        ExecutionResourceStatus.PENDING_EXECUTION,
        ExecutionActionType.MARKETPLACE_CLASS_PURCHASE,
        "intent-" + resourceId,
        ExecutionIntentStatus.PENDING_ONCHAIN,
        LocalDateTime.parse("2026-01-10T10:00:00"),
        1_768_224_000L,
        ExecutionMode.EIP7702,
        1,
        transactionId,
        transactionStatus,
        transactionId == null ? null : "0xtx" + resourceId);
  }
}
