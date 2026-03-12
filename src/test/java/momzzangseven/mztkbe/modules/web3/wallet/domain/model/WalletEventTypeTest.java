package momzzangseven.mztkbe.modules.web3.wallet.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("WalletEventType unit test")
class WalletEventTypeTest {

  @Test
  @DisplayName("enum constants are not empty")
  void values_notEmpty() {
    assertThat(WalletEventType.values()).isNotEmpty();
  }

  @Test
  @DisplayName("valueOf works for declared names")
  void valueOf_roundTrip() {
    for (WalletEventType value : WalletEventType.values()) {
      assertThat(WalletEventType.valueOf(value.name())).isEqualTo(value);
    }
  }

  @Test
  @DisplayName("invalid enum name throws exception")
  void valueOf_invalidName_throwsException() {
    assertThatThrownBy(() -> WalletEventType.valueOf("__INVALID__"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
