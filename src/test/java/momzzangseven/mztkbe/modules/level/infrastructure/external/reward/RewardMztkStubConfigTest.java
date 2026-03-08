package momzzangseven.mztkbe.modules.level.infrastructure.external.reward;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.level.application.port.out.RewardMztkCommand;
import momzzangseven.mztkbe.modules.level.application.port.out.RewardMztkPort;
import momzzangseven.mztkbe.modules.level.application.port.out.RewardMztkResult;
import momzzangseven.mztkbe.modules.level.domain.vo.RewardTxStatus;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import org.junit.jupiter.api.Test;

class RewardMztkStubConfigTest {

  @Test
  void rewardMztkPort_shouldReturnCreatedNotImplemented() {
    RewardMztkPort port = new RewardMztkStubConfig().rewardMztkPort();

    RewardMztkResult result =
        port.reward(
            new RewardMztkCommand(
                1L, 10, 100L, EvmAddress.of("0x1111111111111111111111111111111111111111")));

    assertThat(result.status()).isEqualTo(RewardTxStatus.CREATED);
    assertThat(result.failureReason()).isEqualTo("NOT_IMPLEMENTED");
  }
}
