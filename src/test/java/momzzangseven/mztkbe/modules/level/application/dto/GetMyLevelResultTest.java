package momzzangseven.mztkbe.modules.level.application.dto;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GetMyLevelResult unit test")
class GetMyLevelResultTest {

  @Test
  @DisplayName("validate rejects level below one")
  void validate_levelBelowOne_throwsException() {
    GetMyLevelResult result = new GetMyLevelResult(0, 0, 0, 0);

    assertThatThrownBy(result::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Level must be >= 1");
  }

  @Test
  @DisplayName("validate rejects negative available XP")
  void validate_negativeAvailableXp_throwsException() {
    GetMyLevelResult result = new GetMyLevelResult(1, -1, 0, 0);

    assertThatThrownBy(result::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Available XP must be >= 0");
  }

  @Test
  @DisplayName("validate rejects negative required XP")
  void validate_negativeRequiredXp_throwsException() {
    GetMyLevelResult result = new GetMyLevelResult(1, 0, -1, 0);

    assertThatThrownBy(result::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Required XP must be >= 0");
  }

  @Test
  @DisplayName("validate rejects negative reward")
  void validate_negativeReward_throwsException() {
    GetMyLevelResult result = new GetMyLevelResult(1, 0, 0, -1);

    assertThatThrownBy(result::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Reward MZTK must be >= 0");
  }

  @Test
  @DisplayName("validate passes for non-negative values")
  void validate_validResult_doesNotThrow() {
    GetMyLevelResult result = new GetMyLevelResult(3, 100, 200, 5);

    assertThatCode(result::validate).doesNotThrowAnyException();
  }
}
