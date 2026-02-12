package momzzangseven.mztkbe.modules.level.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.domain.vo.EvmAddress;

/** Outbound port for finding user's ACTIVE wallet address. */
public interface LoadActiveWalletPort {
  Optional<EvmAddress> loadActiveWalletAddress(Long userId);
}
