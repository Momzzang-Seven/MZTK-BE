package momzzangseven.mztkbe.modules.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ExternalDisconnectStatus unit test")
class ExternalDisconnectStatusTest {

  @Test
  @DisplayName("enum constants are not empty")
  void values_notEmpty() {
    assertThat(ExternalDisconnectStatus.values()).isNotEmpty();
  }

  @Test
  @DisplayName("valueOf works for declared names")
  void valueOf_roundTrip() {
    for (ExternalDisconnectStatus value : ExternalDisconnectStatus.values()) {
      assertThat(ExternalDisconnectStatus.valueOf(value.name())).isEqualTo(value);
    }
  }

  @Test
  @DisplayName("invalid enum name throws exception")
  void valueOf_invalidName_throwsException() {
    assertThatThrownBy(() -> ExternalDisconnectStatus.valueOf("__INVALID__"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
