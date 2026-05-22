package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationExecutionCleanupCandidate;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationReceiptTimeout;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.FilterWalletRegistrationExecutionCleanupCandidatesUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "eip7702.cleanup.enabled"},
    havingValue = "true")
public class WalletRegistrationExecutionCleanupProtectionService
    implements FilterWalletRegistrationExecutionCleanupCandidatesUseCase {

  private static final String RESOURCE_WALLET_REGISTRATION = "WALLET_REGISTRATION";
  private static final String ACTION_WALLET_APPROVE = "WALLET_ESCROW_APPROVE";

  private final LoadWalletRegistrationSessionPort loadSessionPort;

  @Override
  @Transactional(readOnly = true)
  public List<Long> filterDeletableFinalizedIntentIds(
      List<WalletRegistrationExecutionCleanupCandidate> candidates) {
    if (candidates == null || candidates.isEmpty()) {
      return List.of();
    }
    return candidates.stream()
        .filter(candidate -> !isProtected(candidate))
        .map(WalletRegistrationExecutionCleanupCandidate::id)
        .toList();
  }

  private boolean isProtected(WalletRegistrationExecutionCleanupCandidate candidate) {
    if (!isWalletApproval(candidate)) {
      return false;
    }
    return loadSessionPort
        .loadByLatestExecutionIntentId(candidate.executionIntentId())
        .map(this::requiresRecoveryReference)
        .orElse(false);
  }

  private boolean isWalletApproval(WalletRegistrationExecutionCleanupCandidate candidate) {
    return RESOURCE_WALLET_REGISTRATION.equals(candidate.resourceType())
        && ACTION_WALLET_APPROVE.equals(candidate.actionType());
  }

  private boolean requiresRecoveryReference(WalletRegistrationSession session) {
    return session.getStatus().isNonTerminal()
        || WalletRegistrationReceiptTimeout.isRecordedOn(session);
  }
}
