package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RegisterWalletCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RegisterWalletResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionDraft;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionRequest;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionWriteView;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.BuildWalletApprovalExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.CreateWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationPolicyPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.MarkWalletRegistrationChallengeUsedPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SubmitWalletApprovalExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional attempt that creates the approval intent and pending registration session. */
@Service
@RequiredArgsConstructor
public class RegisterWalletApprovalAttemptService {

  private final BuildWalletApprovalExecutionDraftPort buildWalletApprovalExecutionDraftPort;
  private final SubmitWalletApprovalExecutionDraftPort submitWalletApprovalExecutionDraftPort;
  private final CreateWalletRegistrationSessionPort createWalletRegistrationSessionPort;
  private final MarkWalletRegistrationChallengeUsedPort markChallengeUsedPort;
  private final LoadWalletRegistrationPolicyPort registrationPolicyPort;
  private final Clock appClock;

  /** Creates the pending session and approval intent atomically. */
  @Transactional
  public RegisterWalletResult createPendingApproval(RegisterWalletCommand command) {
    String registrationId = UUID.randomUUID().toString();
    LocalDateTime now = LocalDateTime.now(appClock);
    LocalDateTime sessionExpiresAt = now.plusSeconds(registrationPolicyPort.sessionTtlSeconds());

    WalletApprovalExecutionDraft draft =
        buildWalletApprovalExecutionDraftPort.build(
            new WalletApprovalExecutionRequest(
                registrationId, command.userId(), command.walletAddress()));
    WalletApprovalExecutionIntentResult approvalIntent =
        submitWalletApprovalExecutionDraftPort.submit(draft);

    WalletRegistrationSession session =
        WalletRegistrationSession.create(
                registrationId,
                command.userId(),
                command.walletAddress(),
                command.nonce(),
                sessionExpiresAt,
                now)
            .attachApprovalIntent(
                approvalIntent.executionIntent().id(),
                sessionExpiresAt,
                LocalDateTime.now(appClock));

    WalletRegistrationSession savedSession =
        createWalletRegistrationSessionPort.createAndFlush(session);
    markChallengeUsedPort.markUsed(command.nonce());

    return RegisterWalletResult.pending(
        savedSession, WalletApprovalExecutionWriteView.from(approvalIntent));
  }
}
