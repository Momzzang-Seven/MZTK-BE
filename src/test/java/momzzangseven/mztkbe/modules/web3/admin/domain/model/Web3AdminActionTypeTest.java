package momzzangseven.mztkbe.modules.web3.admin.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Web3AdminActionType unit test")
class Web3AdminActionTypeTest {

  @Test
  @DisplayName("enum constants are not empty")
  void values_notEmpty() {
    assertThat(Web3AdminActionType.values()).isNotEmpty();
  }

  @Test
  @DisplayName("valueOf works for declared names")
  void valueOf_roundTrip() {
    for (Web3AdminActionType value : Web3AdminActionType.values()) {
      assertThat(Web3AdminActionType.valueOf(value.name())).isEqualTo(value);
    }
  }

  @Test
  @DisplayName("invalid enum name throws exception")
  void valueOf_invalidName_throwsException() {
    assertThatThrownBy(() -> Web3AdminActionType.valueOf("__INVALID__"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
