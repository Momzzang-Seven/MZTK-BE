package momzzangseven.mztkbe.modules.web3.transaction.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Web3TransactionAuditEventType unit test")
class Web3TransactionAuditEventTypeTest {

  @Test
  @DisplayName("enum constants are not empty")
  void values_notEmpty() {
    assertThat(Web3TransactionAuditEventType.values()).isNotEmpty();
  }

  @Test
  @DisplayName("valueOf works for declared names")
  void valueOf_roundTrip() {
    for (Web3TransactionAuditEventType value : Web3TransactionAuditEventType.values()) {
      assertThat(Web3TransactionAuditEventType.valueOf(value.name())).isEqualTo(value);
    }
  }

  @Test
  @DisplayName("invalid enum name throws exception")
  void valueOf_invalidName_throwsException() {
    assertThatThrownBy(() -> Web3TransactionAuditEventType.valueOf("__INVALID__"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
