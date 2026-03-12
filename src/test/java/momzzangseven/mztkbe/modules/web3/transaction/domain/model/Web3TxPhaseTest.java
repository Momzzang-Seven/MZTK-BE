package momzzangseven.mztkbe.modules.web3.transaction.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Web3TxPhase unit test")
class Web3TxPhaseTest {

  @Test
  @DisplayName("from maps success and failure statuses")
  void from_mapsSuccessAndFailure() {
    assertThat(Web3TxPhase.from(Web3TxStatus.SUCCEEDED)).isEqualTo(Web3TxPhase.SUCCESS);
    assertThat(Web3TxPhase.from(Web3TxStatus.FAILED_ONCHAIN)).isEqualTo(Web3TxPhase.FAILED);
  }

  @Test
  @DisplayName("from maps pending-like statuses to PENDING")
  void from_mapsPendingStatuses() {
    assertThat(Web3TxPhase.from(Web3TxStatus.CREATED)).isEqualTo(Web3TxPhase.PENDING);
    assertThat(Web3TxPhase.from(Web3TxStatus.SIGNED)).isEqualTo(Web3TxPhase.PENDING);
    assertThat(Web3TxPhase.from(Web3TxStatus.PENDING)).isEqualTo(Web3TxPhase.PENDING);
    assertThat(Web3TxPhase.from(Web3TxStatus.UNCONFIRMED)).isEqualTo(Web3TxPhase.PENDING);
  }

  @Test
  @DisplayName("valueOf rejects invalid name")
  void valueOf_invalidName_throwsException() {
    assertThatThrownBy(() -> Web3TxPhase.valueOf("__INVALID__"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
