package momzzangseven.mztkbe.modules.web3.transfer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TransferGuardAuditReason unit test")
class TransferGuardAuditReasonTest {

  @Test
  @DisplayName("enum constants are not empty")
  void values_notEmpty() {
    assertThat(TransferGuardAuditReason.values()).isNotEmpty();
  }

  @Test
  @DisplayName("valueOf works for declared names")
  void valueOf_roundTrip() {
    for (TransferGuardAuditReason value : TransferGuardAuditReason.values()) {
      assertThat(TransferGuardAuditReason.valueOf(value.name())).isEqualTo(value);
    }
  }

  @Test
  @DisplayName("invalid enum name throws exception")
  void valueOf_invalidName_throwsException() {
    assertThatThrownBy(() -> TransferGuardAuditReason.valueOf("__INVALID__"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
