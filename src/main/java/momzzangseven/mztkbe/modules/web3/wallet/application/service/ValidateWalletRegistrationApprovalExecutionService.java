package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.ValidateWalletRegistrationApprovalExecutionCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.ValidateWalletRegistrationApprovalExecutionUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Validates wallet approval execution ownership before transaction execution. */
@Service
@RequiredArgsConstructor
public class ValidateWalletRegistrationApprovalExecutionService
    implements ValidateWalletRegistrationApprovalExecutionUseCase {

  private final LoadWalletRegistrationSessionPort loadSessionPort;
  private final Clock appClock;

  @Override
  @Transactional(readOnly = true)
  public void execute(ValidateWalletRegistrationApprovalExecutionCommand command) {
    WalletRegistrationSession session =
        loadSessionPort
            .loadByPublicId(command.registrationId())
            .orElseThrow(
                () ->
                    new Web3InvalidInputException(
                        "registrationId not found: " + command.registrationId()));
    if (!session.getUserId().equals(command.requesterUserId())) {
      throw new Web3InvalidInputException("wallet registration owner mismatch");
    }
    if (session.getLatestExecutionIntentId() == null
        || !session.getLatestExecutionIntentId().equals(command.executionIntentId())) {
      throw new Web3InvalidInputException("approval execution is not latest for registration");
    }
    LocalDateTime now = LocalDateTime.now(appClock);
    if (session.getApprovalExpiresAt() != null && !session.getApprovalExpiresAt().isAfter(now)) {
      throw new Web3InvalidInputException("wallet registration session is expired");
    }
    if (session.getStatus() != WalletRegistrationStatus.APPROVAL_REQUIRED
        && session.getStatus() != WalletRegistrationStatus.APPROVAL_SIGNED
        && session.getStatus() != WalletRegistrationStatus.APPROVAL_PENDING_ONCHAIN) {
      throw new Web3InvalidInputException("wallet registration is not executable");
    }
  }
}
