package momzzangseven.mztkbe.modules.web3.wallet.application.port.out;

import java.util.List;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;

/** Loads wallet registration sessions that need scheduled recovery reconciliation. */
public interface LoadWalletRegistrationRecoveryCandidatePort {

  List<WalletRegistrationSession> loadRecoveryCandidates(int limit);
}
