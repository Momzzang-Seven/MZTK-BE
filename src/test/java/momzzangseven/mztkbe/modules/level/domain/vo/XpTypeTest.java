package momzzangseven.mztkbe.modules.level.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("XpType unit test")
class XpTypeTest {

  @Test
  @DisplayName("enum constants are not empty")
  void values_notEmpty() {
    assertThat(XpType.values()).isNotEmpty();
  }

  @Test
  @DisplayName("valueOf works for declared names")
  void valueOf_roundTrip() {
    for (XpType value : XpType.values()) {
      assertThat(XpType.valueOf(value.name())).isEqualTo(value);
    }
  }

  @Test
  @DisplayName("invalid enum name throws exception")
  void valueOf_invalidName_throwsException() {
    assertThatThrownBy(() -> XpType.valueOf("__INVALID__"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
