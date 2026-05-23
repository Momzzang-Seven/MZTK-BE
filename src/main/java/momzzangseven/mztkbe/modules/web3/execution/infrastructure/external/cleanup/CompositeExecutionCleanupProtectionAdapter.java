package momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.cleanup;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionIntentCleanupView;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentCleanupProtectionPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.in.FilterMarketplaceExecutionCleanupCandidatesUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.FilterQnaExecutionCleanupCandidatesUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationExecutionCleanupCandidate;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.FilterWalletRegistrationExecutionCleanupCandidatesUseCase;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Routes finalized shared execution cleanup candidates through feature-specific protection checks.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "eip7702.cleanup.enabled"},
    havingValue = "true")
public class CompositeExecutionCleanupProtectionAdapter
    implements ExecutionIntentCleanupProtectionPort {

  private final ExecutionIntentPersistencePort executionIntentPersistencePort;
  private final ObjectProvider<FilterQnaExecutionCleanupCandidatesUseCase> qnaProtectionProvider;
  private final ObjectProvider<FilterMarketplaceExecutionCleanupCandidatesUseCase>
      marketplaceProtectionProvider;
  private final ObjectProvider<FilterWalletRegistrationExecutionCleanupCandidatesUseCase>
      walletRegistrationProtectionProvider;

  @Override
  public List<Long> filterDeletableFinalizedIntentIds(List<Long> candidateIntentIds) {
    if (candidateIntentIds == null || candidateIntentIds.isEmpty()) {
      return List.of();
    }

    List<ExecutionIntentCleanupView> views =
        executionIntentPersistencePort.findAllByIdsForUpdate(candidateIntentIds).stream()
            .map(this::toCleanupView)
            .toList();
    List<Long> qnaIds =
        views.stream().filter(this::isQnaIntent).map(ExecutionIntentCleanupView::id).toList();
    List<Long> marketplaceIds =
        views.stream()
            .filter(this::isMarketplaceUserIntent)
            .map(ExecutionIntentCleanupView::id)
            .toList();
    List<WalletRegistrationExecutionCleanupCandidate> walletRegistrationCandidates =
        views.stream()
            .filter(this::isWalletRegistrationIntent)
            .map(this::toWalletRegistrationCandidate)
            .toList();
    Set<Long> routedIds = new HashSet<>();
    routedIds.addAll(qnaIds);
    routedIds.addAll(marketplaceIds);
    walletRegistrationCandidates.stream()
        .map(WalletRegistrationExecutionCleanupCandidate::id)
        .forEach(routedIds::add);

    Set<Long> deletableIds = new HashSet<>();
    views.stream()
        .map(ExecutionIntentCleanupView::id)
        .filter(id -> !routedIds.contains(id))
        .forEach(deletableIds::add);

    FilterQnaExecutionCleanupCandidatesUseCase qnaProtection =
        qnaProtectionProvider.getIfAvailable();
    if (!qnaIds.isEmpty()) {
      if (qnaProtection == null) {
        log.warn("QnA execution cleanup protection is unavailable; keeping intents: {}", qnaIds);
      } else {
        deletableIds.addAll(qnaProtection.filterDeletableFinalizedIntentIds(qnaIds));
      }
    }

    FilterMarketplaceExecutionCleanupCandidatesUseCase marketplaceProtection =
        marketplaceProtectionProvider.getIfAvailable();
    if (!marketplaceIds.isEmpty()) {
      if (marketplaceProtection == null) {
        log.warn(
            "Marketplace execution cleanup protection is unavailable; keeping intents: {}",
            marketplaceIds);
      } else {
        deletableIds.addAll(
            marketplaceProtection.filterDeletableFinalizedIntentIds(marketplaceIds));
      }
    }

    FilterWalletRegistrationExecutionCleanupCandidatesUseCase walletRegistrationProtection =
        walletRegistrationProtectionProvider.getIfAvailable();
    if (!walletRegistrationCandidates.isEmpty()) {
      if (walletRegistrationProtection == null) {
        log.warn(
            "Wallet registration execution cleanup protection is unavailable; keeping intents: {}",
            walletRegistrationCandidates.stream()
                .map(WalletRegistrationExecutionCleanupCandidate::id)
                .toList());
      } else {
        deletableIds.addAll(
            walletRegistrationProtection.filterDeletableFinalizedIntentIds(
                walletRegistrationCandidates));
      }
    }

    return candidateIntentIds.stream().filter(deletableIds::contains).toList();
  }

  private boolean isQnaIntent(ExecutionIntentCleanupView view) {
    return view.actionType().name().startsWith("QNA_");
  }

  private ExecutionIntentCleanupView toCleanupView(ExecutionIntent intent) {
    return new ExecutionIntentCleanupView(
        intent.getId(),
        intent.getPublicId(),
        intent.getResourceType(),
        intent.getResourceId(),
        intent.getActionType(),
        intent.getRequesterUserId(),
        intent.getPayloadSnapshotJson());
  }

  private boolean isMarketplaceUserIntent(ExecutionIntentCleanupView view) {
    ExecutionActionType actionType = view.actionType();
    return actionType == ExecutionActionType.MARKETPLACE_CLASS_PURCHASE
        || actionType == ExecutionActionType.MARKETPLACE_CLASS_CANCEL
        || actionType == ExecutionActionType.MARKETPLACE_CLASS_CONFIRM
        || actionType == ExecutionActionType.MARKETPLACE_CLASS_EXPIRED_REFUND;
  }

  private boolean isWalletRegistrationIntent(ExecutionIntentCleanupView view) {
    return view.resourceType() == ExecutionResourceType.WALLET_REGISTRATION
        && view.actionType() == ExecutionActionType.WALLET_ESCROW_APPROVE;
  }

  private WalletRegistrationExecutionCleanupCandidate toWalletRegistrationCandidate(
      ExecutionIntentCleanupView view) {
    return new WalletRegistrationExecutionCleanupCandidate(
        view.id(),
        view.publicId(),
        view.resourceId(),
        view.resourceType().name(),
        view.actionType().name());
  }
}
