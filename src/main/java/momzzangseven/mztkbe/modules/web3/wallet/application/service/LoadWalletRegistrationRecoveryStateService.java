package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.LoadWalletRegistrationRecoveryStateQuery;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationRecoveryStateResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.LoadWalletRegistrationRecoveryStateUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;

@RequiredArgsConstructor
public class LoadWalletRegistrationRecoveryStateService
    implements LoadWalletRegistrationRecoveryStateUseCase {

  private final LoadWalletRegistrationSessionPort loadSessionPort;

  @Override
  public Optional<WalletRegistrationRecoveryStateResult> execute(
      LoadWalletRegistrationRecoveryStateQuery query) {
    if (query == null) {
      throw new Web3InvalidInputException("query is required");
    }
    return loadSessionPort
        .loadByPublicId(query.registrationId())
        .map(
            session ->
                WalletRegistrationRecoveryStateResult.from(
                    session, hasNewerAuthoritativeSession(session)));
  }

  private boolean hasNewerAuthoritativeSession(WalletRegistrationSession session) {
    return session.getId() != null
        && loadSessionPort.existsNewerByUserIdOrWalletAddress(
            session.getUserId(), session.getWalletAddress(), session.getId());
  }
}
