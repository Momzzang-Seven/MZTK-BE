package momzzangseven.mztkbe.modules.level.application.port.out;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.level.LevelUpCommandInvalidException;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import org.junit.jupiter.api.Test;

class RewardMztkCommandTest {

  @Test
  void constructor_acceptsValidPayload() {
    assertThatCode(() -> new RewardMztkCommand(1L, 10, 77L, EvmAddress.of("0x" + "a".repeat(40))))
        .doesNotThrowAnyException();
  }

  @Test
  void constructor_rejectsNullOrNonPositiveUserId() {
    assertThatThrownBy(
            () -> new RewardMztkCommand(null, 10, 77L, EvmAddress.of("0x" + "a".repeat(40))))
        .isInstanceOf(LevelUpCommandInvalidException.class);

    assertThatThrownBy(
            () -> new RewardMztkCommand(0L, 10, 77L, EvmAddress.of("0x" + "a".repeat(40))))
        .isInstanceOf(LevelUpCommandInvalidException.class);
  }

  @Test
  void constructor_rejectsNullOrNonPositiveReferenceId() {
    assertThatThrownBy(
            () -> new RewardMztkCommand(1L, 10, null, EvmAddress.of("0x" + "a".repeat(40))))
        .isInstanceOf(LevelUpCommandInvalidException.class);

    assertThatThrownBy(
            () -> new RewardMztkCommand(1L, 10, 0L, EvmAddress.of("0x" + "a".repeat(40))))
        .isInstanceOf(LevelUpCommandInvalidException.class);
  }

  @Test
  void constructor_rejectsNegativeReward() {
    assertThatThrownBy(
            () -> new RewardMztkCommand(1L, -1, 77L, EvmAddress.of("0x" + "a".repeat(40))))
        .isInstanceOf(LevelUpCommandInvalidException.class);
  }

  @Test
  void constructor_rejectsNullToWalletAddress() {
    assertThatThrownBy(() -> new RewardMztkCommand(1L, 10, 77L, null))
        .isInstanceOf(LevelUpCommandInvalidException.class);
  }
}
