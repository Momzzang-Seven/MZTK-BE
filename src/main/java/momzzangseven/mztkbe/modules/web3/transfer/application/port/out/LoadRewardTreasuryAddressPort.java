package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import java.util.Optional;

/**
 * Transfer-side bridging port for the reward treasury address lookup. Implemented by an adapter
 * under {@code infrastructure/external/treasury/} that delegates to the treasury module's {@code
 * LoadTreasuryWalletByRoleUseCase} with the {@code REWARD} role bound inside the adapter;
 * transfer-layer callers never import treasury types directly.
 *
 * <p>Mirrors the {@code transaction.LoadRewardTreasuryWalletPort} sidecar pattern but exposes only
 * the wallet address — the level-up reward path needs the {@code from} address for the on-chain
 * transfer intent and nothing else.
 */
public interface LoadRewardTreasuryAddressPort {

  /**
   * @return the reward treasury wallet address, or {@link Optional#empty()} when the treasury row
   *     is absent or the address column is blank
   */
  Optional<String> loadAddress();
}
