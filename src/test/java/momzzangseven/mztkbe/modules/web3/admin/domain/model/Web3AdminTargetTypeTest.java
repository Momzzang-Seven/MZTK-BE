package momzzangseven.mztkbe.modules.web3.admin.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Web3AdminTargetType unit test")
class Web3AdminTargetTypeTest {

  @Test
  @DisplayName("enum constants are not empty")
  void values_notEmpty() {
    assertThat(Web3AdminTargetType.values()).isNotEmpty();
  }

  @Test
  @DisplayName("valueOf works for declared names")
  void valueOf_roundTrip() {
    for (Web3AdminTargetType value : Web3AdminTargetType.values()) {
      assertThat(Web3AdminTargetType.valueOf(value.name())).isEqualTo(value);
    }
  }

  @Test
  @DisplayName("invalid enum name throws exception")
  void valueOf_invalidName_throwsException() {
    assertThatThrownBy(() -> Web3AdminTargetType.valueOf("__INVALID__"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
