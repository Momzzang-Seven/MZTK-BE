package momzzangseven.mztkbe.modules.web3.qna.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("QnaExecutionIntentStatus")
class QnaExecutionIntentStatusTest {

  @Test
  @DisplayName("active statuses are awaiting signature, signed, and pending onchain")
  void isActive() {
    assertThat(QnaExecutionIntentStatus.AWAITING_SIGNATURE.isActive()).isTrue();
    assertThat(QnaExecutionIntentStatus.SIGNED.isActive()).isTrue();
    assertThat(QnaExecutionIntentStatus.PENDING_ONCHAIN.isActive()).isTrue();
    assertThat(QnaExecutionIntentStatus.CONFIRMED.isActive()).isFalse();
  }

  @Test
  @DisplayName("terminal failure excludes confirmed")
  void isTerminalFailure() {
    assertThat(QnaExecutionIntentStatus.CONFIRMED.isTerminal()).isTrue();
    assertThat(QnaExecutionIntentStatus.CONFIRMED.isTerminalFailure()).isFalse();
    assertThat(QnaExecutionIntentStatus.EXPIRED.isTerminalFailure()).isTrue();
    assertThat(QnaExecutionIntentStatus.FAILED_ONCHAIN.isTerminalFailure()).isTrue();
    assertThat(QnaExecutionIntentStatus.NONCE_STALE.isTerminalFailure()).isTrue();
  }
}
