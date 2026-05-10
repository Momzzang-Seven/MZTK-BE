package momzzangseven.mztkbe.modules.web3.treasury.application.port.in;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryRole;

/**
 * System-internal lookup of the {@code TreasuryWallet} bound to a {@link TreasuryRole}.
 *
 * <p>Distinct from {@link LoadTreasuryWalletUseCase}: that one is admin-audited
 * ({@code @AdminOnly}) and parameterised by alias + operator id, suiting the admin console. This
 * use case is for scheduler / worker call sites that need to resolve the canonical wallet for a
 * role without leaking an admin audit row per batch.
 */
public interface LoadTreasuryWalletByRoleUseCase {

  Optional<TreasuryWalletView> execute(TreasuryRole role);
}
