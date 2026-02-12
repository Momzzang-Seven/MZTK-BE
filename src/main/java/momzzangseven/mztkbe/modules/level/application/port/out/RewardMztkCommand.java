package momzzangseven.mztkbe.modules.level.application.port.out;

import lombok.Builder;
import momzzangseven.mztkbe.modules.web3.domain.vo.EvmAddress;

/** Command for issuing a level-up reward via {@code RewardMztkPort}. */
@Builder
public record RewardMztkCommand(
    Long userId, int rewardMztk, Long referenceId, EvmAddress toWalletAddress) {}
