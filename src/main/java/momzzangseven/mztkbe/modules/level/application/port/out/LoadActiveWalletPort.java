package momzzangseven.mztkbe.modules.level.application.port.out;

import java.util.Optional;

/** Outbound port for finding user's ACTIVE wallet address. */
public interface LoadActiveWalletPort {
  Optional<String> loadActiveWalletAddress(Long userId);
}
