package momzzangseven.mztkbe.modules.web3.execution.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ExecuteInternalExecutionIntentResult — factory methods")
class ExecuteInternalExecutionIntentResultTest {

  @Test
  @DisplayName("[M-27] notFound() — executed/quarantined 모두 false, 모든 ID/상태/txHash 필드 null")
  void notFound_returnsAllNullsAndFalseFlags() {
    ExecuteInternalExecutionIntentResult result = ExecuteInternalExecutionIntentResult.notFound();

    assertThat(result.executed()).isFalse();
    assertThat(result.quarantined()).isFalse();
    assertThat(result.executionIntentId()).isNull();
    assertThat(result.executionIntentStatus()).isNull();
    assertThat(result.transactionId()).isNull();
    assertThat(result.transactionStatus()).isNull();
    assertThat(result.txHash()).isNull();
  }

  @Test
  @DisplayName(
      "[M-28] quarantined(intentId, status) — executed=true, quarantined=true, 인텐트 식별자만 채워짐")
  void quarantined_executedAndQuarantinedTrue_intentIdsPopulated() {
    ExecuteInternalExecutionIntentResult result =
        ExecuteInternalExecutionIntentResult.quarantined(
            "intent-q", ExecutionIntentStatus.CANCELED);

    assertThat(result.executed()).isTrue();
    assertThat(result.quarantined()).isTrue();
    assertThat(result.executionIntentId()).isEqualTo("intent-q");
    assertThat(result.executionIntentStatus()).isEqualTo(ExecutionIntentStatus.CANCELED);
    assertThat(result.transactionId()).isNull();
    assertThat(result.transactionStatus()).isNull();
    assertThat(result.txHash()).isNull();
  }

  @Test
  @DisplayName(
      "[M-29] transientRetry(intentId, status) — executed=false (batch break) but 인텐트 식별자 보존")
  void transientRetry_executedFalseAndIntentIdsPreserved() {
    ExecuteInternalExecutionIntentResult result =
        ExecuteInternalExecutionIntentResult.transientRetry(
            "intent-throttled", ExecutionIntentStatus.AWAITING_SIGNATURE);

    assertThat(result.executed()).isFalse();
    assertThat(result.quarantined()).isFalse();
    assertThat(result.executionIntentId()).isEqualTo("intent-throttled");
    assertThat(result.executionIntentStatus()).isEqualTo(ExecutionIntentStatus.AWAITING_SIGNATURE);
    assertThat(result.transactionId()).isNull();
    assertThat(result.transactionStatus()).isNull();
    assertThat(result.txHash()).isNull();
  }

  @Test
  @DisplayName("[M-30] preflightSkipped() — executed=false, intent 도 claim 안 한 상태이므로 모든 ID/상태 null")
  void preflightSkipped_returnsAllNullsAndFalseFlags() {
    ExecuteInternalExecutionIntentResult result =
        ExecuteInternalExecutionIntentResult.preflightSkipped();

    assertThat(result.executed()).isFalse();
    assertThat(result.quarantined()).isFalse();
    assertThat(result.executionIntentId()).isNull();
    assertThat(result.executionIntentStatus()).isNull();
    assertThat(result.transactionId()).isNull();
    assertThat(result.transactionStatus()).isNull();
    assertThat(result.txHash()).isNull();
  }
}
