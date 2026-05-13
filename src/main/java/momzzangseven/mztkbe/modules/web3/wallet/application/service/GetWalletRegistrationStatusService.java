package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.wallet.WalletNotFoundException;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.GetWalletRegistrationStatusQuery;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionStateView;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationStatusResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.GetWalletRegistrationStatusUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalExecutionStatePort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only owner-bound wallet registration status service. */
@Service
@RequiredArgsConstructor
public class GetWalletRegistrationStatusService implements GetWalletRegistrationStatusUseCase {

  private final LoadWalletRegistrationSessionPort loadSessionPort;
  private final LoadWalletApprovalExecutionStatePort loadExecutionStatePort;
  private final Clock appClock;

  @Override
  @Transactional(readOnly = true)
  public WalletRegistrationStatusResult execute(GetWalletRegistrationStatusQuery query) {
    WalletRegistrationSession session =
        loadSessionPort
            .loadByPublicIdAndUserId(query.registrationId(), query.requesterUserId())
            .orElseThrow(WalletNotFoundException::new);
    Optional<WalletApprovalExecutionStateView> executionState = loadExecutionState(session);
    return WalletRegistrationStatusResult.from(
        session, executionState.orElse(null), LocalDateTime.now(appClock));
  }

  private Optional<WalletApprovalExecutionStateView> loadExecutionState(
      WalletRegistrationSession session) {
    if (session.getLatestExecutionIntentId() == null) {
      return Optional.empty();
    }
    return loadExecutionStatePort.loadByExecutionIntentId(
        session.getUserId(), session.getLatestExecutionIntentId());
  }
}
