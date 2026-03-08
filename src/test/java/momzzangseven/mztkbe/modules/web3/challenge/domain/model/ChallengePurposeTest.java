package momzzangseven.mztkbe.modules.web3.challenge.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ChallengePurpose unit test")
class ChallengePurposeTest {

  @Test
  @DisplayName("enum constants are not empty")
  void values_notEmpty() {
    assertThat(ChallengePurpose.values()).isNotEmpty();
  }

  @Test
  @DisplayName("valueOf works for declared names")
  void valueOf_roundTrip() {
    for (ChallengePurpose value : ChallengePurpose.values()) {
      assertThat(ChallengePurpose.valueOf(value.name())).isEqualTo(value);
    }
  }

  @Test
  @DisplayName("invalid enum name throws exception")
  void valueOf_invalidName_throwsException() {
    assertThatThrownBy(() -> ChallengePurpose.valueOf("__INVALID__"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
