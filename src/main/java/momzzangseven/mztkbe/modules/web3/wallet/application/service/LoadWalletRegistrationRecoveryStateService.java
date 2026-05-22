package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.LoadWalletRegistrationRecoveryStateQuery;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationRecoveryStateResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.LoadWalletRegistrationRecoveryStateUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationSessionPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoadWalletRegistrationRecoveryStateService
    implements LoadWalletRegistrationRecoveryStateUseCase {

  private final LoadWalletRegistrationSessionPort loadSessionPort;

  @Override
  @Transactional(readOnly = true)
  public Optional<WalletRegistrationRecoveryStateResult> execute(
      LoadWalletRegistrationRecoveryStateQuery query) {
    if (query == null) {
      throw new Web3InvalidInputException("query is required");
    }
    return loadSessionPort
        .loadByPublicId(query.registrationId())
        .map(WalletRegistrationRecoveryStateResult::from);
  }
}
