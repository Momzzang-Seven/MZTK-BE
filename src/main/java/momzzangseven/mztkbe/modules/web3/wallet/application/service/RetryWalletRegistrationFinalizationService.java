package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.FinalizeWalletRegistrationCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RetryWalletRegistrationFinalizationCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.FinalizeWalletRegistrationUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.RetryWalletRegistrationFinalizationUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationSessionPort;
import org.springframework.stereotype.Service;

/** Operator/recovery entry point for retrying local finalization without a new wallet signature. */
@Service
@RequiredArgsConstructor
public class RetryWalletRegistrationFinalizationService
    implements RetryWalletRegistrationFinalizationUseCase {

  private final LoadWalletRegistrationSessionPort loadSessionPort;
  private final FinalizeWalletRegistrationUseCase finalizeWalletRegistrationUseCase;

  @Override
  public void execute(RetryWalletRegistrationFinalizationCommand command) {
    loadSessionPort
        .loadByPublicId(command.registrationId())
        .filter(session -> session.getStatus().isConfirmedButNotFinalized())
        .filter(session -> session.getLatestExecutionIntentId() != null)
        .ifPresent(
            session ->
                finalizeWalletRegistrationUseCase.execute(
                    new FinalizeWalletRegistrationCommand(
                        session.getPublicId(), session.getLatestExecutionIntentId())));
  }
}
