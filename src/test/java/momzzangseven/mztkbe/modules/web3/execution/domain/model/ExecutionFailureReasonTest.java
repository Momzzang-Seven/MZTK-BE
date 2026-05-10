package momzzangseven.mztkbe.modules.web3.execution.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Compile/test-time guard for the silent contract documented in {@link ExecutionFailureReason}'s
 * JavaDoc: every value's {@code name()} must round-trip through {@link
 * Web3TxFailureReason#valueOf(String)}.
 *
 * <p>Listeners on {@link
 * momzzangseven.mztkbe.modules.web3.execution.domain.event.ExecutionIntentTerminatedEvent} (most
 * notably {@code QnaEscrowExecutionActionHandlerAdapter}) parse the published {@code failureReason}
 * String back into {@link Web3TxFailureReason} via {@code valueOf}. If a future commit adds a new
 * value to {@link ExecutionFailureReason} without mirroring it on the transaction-side enum, that
 * listener silently routes the unknown reason as if it were absent — escrow rollback / refund logic
 * could then misbehave with no compile-time signal. This test fails loud at the build boundary.
 */
@DisplayName("ExecutionFailureReason ↔ Web3TxFailureReason name() 동기화")
class ExecutionFailureReasonTest {

  @Test
  @DisplayName("모든 ExecutionFailureReason 값은 Web3TxFailureReason.valueOf 로 round-trip 가능해야 한다")
  void everyValue_roundTripsThroughWeb3TxFailureReasonValueOf() {
    for (ExecutionFailureReason reason : ExecutionFailureReason.values()) {
      assertThatNoException()
          .as(
              "ExecutionFailureReason.%s 의 name() 이 Web3TxFailureReason 에 누락. 두 enum"
                  + " 동기화가 깨진 상태 — listener (e.g. QnaEscrowExecutionActionHandlerAdapter)"
                  + " 가 String → enum 역변환 시 회귀.",
              reason.name())
          .isThrownBy(() -> Web3TxFailureReason.valueOf(reason.name()));
    }
  }

  @Test
  @DisplayName("매핑된 Web3TxFailureReason 의 retryable=false 보장")
  void everyValue_mapsToNonRetryableWeb3TxFailureReason() {
    // ExecutionFailureReason 은 본질적으로 terminal failure 만 표현 (cancelEip7702IntentAndCascade /
    // quarantineInvalidIntent 두 경로에서만 사용). 대응되는 Web3TxFailureReason 값이 retryable=true 로
    // 바뀌면 escrow rollback 정책이 달라지므로 컴파일 단위가 아니라 의미 단위에서 잡는다.
    for (ExecutionFailureReason reason : ExecutionFailureReason.values()) {
      Web3TxFailureReason mirrored = Web3TxFailureReason.valueOf(reason.name());
      assertThat(mirrored.isRetryable())
          .as(
              "Web3TxFailureReason.%s 가 retryable=true 로 바뀜 — ExecutionFailureReason"
                  + " 측은 terminal 만 사용하므로 의미 충돌.",
              reason.name())
          .isFalse();
    }
  }
}
