package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.level.application.port.out.LoadActiveWalletPort;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletStatus;
import org.springframework.stereotype.Component;

/** Level module adapter for ACTIVE wallet lookup. */
@Component
@RequiredArgsConstructor
public class LevelActiveWalletAdapter implements LoadActiveWalletPort {

  private final LoadWalletPort loadWalletPort;

  @Override
  public Optional<EvmAddress> loadActiveWalletAddress(Long userId) {
    return loadWalletPort.findWalletsByUserIdAndStatus(userId, WalletStatus.ACTIVE).stream()
        .findFirst()
        .map(wallet -> EvmAddress.of(wallet.getWalletAddress()));
  }
}
