package momzzangseven.mztkbe.modules.level.infrastructure.external.reward;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.web3.Web3InternalIssuerDisabledException;
import momzzangseven.mztkbe.modules.level.application.port.out.RewardMztkCommand;
import momzzangseven.mztkbe.modules.level.application.port.out.RewardMztkPort;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import org.junit.jupiter.api.Test;

class RewardMztkUnavailableConfigTest {

  @Test
  void rewardMztkPort_shouldThrowWhenRewardIssuerIsUnavailable() {
    RewardMztkPort port = new RewardMztkUnavailableConfig().rewardMztkPort();

    assertThatThrownBy(
            () ->
                port.reward(
                    new RewardMztkCommand(
                        1L, 10, 100L, EvmAddress.of("0x1111111111111111111111111111111111111111"))))
        .isInstanceOf(Web3InternalIssuerDisabledException.class)
        .hasMessageContaining("LEVEL_UP_REWARD issuer is unavailable");
  }
}
