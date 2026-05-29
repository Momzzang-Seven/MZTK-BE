package momzzangseven.mztkbe.modules.web3.admin.infrastructure.external.transaction.adapter;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.SponsorNonceSlotAdminView;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.LoadSponsorNonceSlotReviewPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceSlotView;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.nonce.ManageNonceSlotLifecycleUseCase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoadSponsorNonceSlotReviewAdapter implements LoadSponsorNonceSlotReviewPort {

  private final ManageNonceSlotLifecycleUseCase manageNonceSlotLifecycleUseCase;

  @Override
  public List<SponsorNonceSlotAdminView> loadSlots(
      long chainId, String fromAddress, int page, int size) {
    return manageNonceSlotLifecycleUseCase
        .loadSlotsForReview(chainId, fromAddress, page, size)
        .stream()
        .map(this::toAdminView)
        .toList();
  }

  private SponsorNonceSlotAdminView toAdminView(SponsorNonceSlotView view) {
    return new SponsorNonceSlotAdminView(
        view.chainId(),
        view.fromAddress(),
        view.nonce(),
        view.status().name(),
        view.attemptNo(),
        view.activeAttemptId(),
        view.activeTxId(),
        view.activeTxHash(),
        view.consumedAttemptId(),
        view.consumedTxId(),
        view.consumedExternalEvidenceId(),
        view.consumedAt(),
        view.consumedReason(),
        view.releasedAttemptId(),
        view.releasedTxId(),
        view.releasedAt(),
        view.releaseReason(),
        view.stuckReason(),
        view.replacementClaimOwner(),
        view.replacementClaimExpiresAt(),
        view.replacementPrepareAttemptCount(),
        view.broadcastStartedAt(),
        view.lastBroadcastedAt(),
        view.broadcastRecoveryClaimOwner(),
        view.broadcastRecoveryClaimExpiresAt(),
        view.broadcastRecoveryAttemptCount(),
        view.createdAt(),
        view.updatedAt());
  }
}
