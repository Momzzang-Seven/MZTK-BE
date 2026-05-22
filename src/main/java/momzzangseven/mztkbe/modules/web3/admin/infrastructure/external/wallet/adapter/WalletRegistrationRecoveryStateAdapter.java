package momzzangseven.mztkbe.modules.web3.admin.infrastructure.external.wallet.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.WalletRegistrationRecoveryStateView;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.LoadWalletRegistrationRecoveryStatePort;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.LoadWalletRegistrationRecoveryStateQuery;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationRecoveryStateResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.LoadWalletRegistrationRecoveryStateUseCase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WalletRegistrationRecoveryStateAdapter
    implements LoadWalletRegistrationRecoveryStatePort {

  private final Optional<LoadWalletRegistrationRecoveryStateUseCase> loadRecoveryStateUseCase;

  @Override
  public Optional<WalletRegistrationRecoveryStateView> load(String registrationId) {
    return loadRecoveryStateUseCase
        .flatMap(
            useCase ->
                useCase.execute(new LoadWalletRegistrationRecoveryStateQuery(registrationId)))
        .map(this::toView);
  }

  private WalletRegistrationRecoveryStateView toView(WalletRegistrationRecoveryStateResult result) {
    return new WalletRegistrationRecoveryStateView(
        result.registrationId(),
        result.userId(),
        result.walletAddress(),
        result.status(),
        result.latestExecutionIntentId(),
        result.latestTransactionId(),
        result.latestTransactionHash(),
        result.lastErrorCode(),
        result.lastErrorReason(),
        result.newerWalletRegistrationExists(),
        result.registeredWalletId());
  }
}
