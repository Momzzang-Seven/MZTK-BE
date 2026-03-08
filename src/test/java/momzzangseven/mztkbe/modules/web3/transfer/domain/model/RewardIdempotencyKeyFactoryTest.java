package momzzangseven.mztkbe.modules.web3.transfer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.junit.jupiter.api.Test;

class RewardIdempotencyKeyFactoryTest {

  @Test
  void forLevelUpReward_returnsStableFormat() {
    String key = RewardIdempotencyKeyFactory.forLevelUpReward(12L, 300L);

    assertThat(key).isEqualTo("reward:12:300");
  }

  @Test
  void forLevelUpReward_throws_whenUserIdInvalid() {
    assertThatThrownBy(() -> RewardIdempotencyKeyFactory.forLevelUpReward(0L, 300L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("userId must be positive");
  }

  @Test
  void forLevelUpReward_throws_whenLevelUpHistoryIdInvalid() {
    assertThatThrownBy(() -> RewardIdempotencyKeyFactory.forLevelUpReward(12L, 0L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("levelUpHistoryId must be positive");
  }
}
