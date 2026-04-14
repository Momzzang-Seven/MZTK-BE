package momzzangseven.mztkbe.modules.web3.transfer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.junit.jupiter.api.Test;

class ResolvedRewardTest {

  @Test
  void constructor_acceptsPositiveValues() {
    ResolvedReward reward = new ResolvedReward(7L, BigInteger.TEN, 15L);

    assertThat(reward.toUserId()).isEqualTo(7L);
    assertThat(reward.amountWei()).isEqualTo(BigInteger.TEN);
    assertThat(reward.acceptedCommentId()).isEqualTo(15L);
  }

  @Test
  void constructor_throws_whenAcceptedCommentIdNotPositive() {
    assertThatThrownBy(() -> new ResolvedReward(7L, BigInteger.TEN, 0L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("acceptedCommentId must be positive when provided");
  }

  @Test
  void constructor_throws_whenToUserIdInvalid() {
    assertThatThrownBy(() -> new ResolvedReward(0L, BigInteger.TEN, 15L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("resolved toUserId must be positive");
  }

  @Test
  void constructor_throws_whenAmountWeiInvalid() {
    assertThatThrownBy(() -> new ResolvedReward(7L, BigInteger.ZERO, 15L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("resolved amountWei must be > 0");
  }
}
