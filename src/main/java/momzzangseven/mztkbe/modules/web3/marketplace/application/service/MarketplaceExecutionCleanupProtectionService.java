package momzzangseven.mztkbe.modules.web3.marketplace.application.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionCleanupIntent;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.in.FilterMarketplaceExecutionCleanupCandidatesUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.CheckMarketplaceReservationCleanupProtectionPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplaceExecutionCleanupIntentPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Protects finalized marketplace execution intents that still have local repair evidence. */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "eip7702.cleanup.enabled"},
    havingValue = "true")
public class MarketplaceExecutionCleanupProtectionService
    implements FilterMarketplaceExecutionCleanupCandidatesUseCase {

  private final LoadMarketplaceExecutionCleanupIntentPort loadMarketplaceExecutionCleanupIntentPort;
  private final CheckMarketplaceReservationCleanupProtectionPort
      checkMarketplaceReservationCleanupProtectionPort;

  @Override
  @Transactional(readOnly = true)
  public List<Long> filterDeletableFinalizedIntentIds(List<Long> candidateIntentIds) {
    if (candidateIntentIds == null || candidateIntentIds.isEmpty()) {
      return List.of();
    }
    List<MarketplaceExecutionCleanupIntent> intents =
        loadMarketplaceExecutionCleanupIntentPort.loadByIds(candidateIntentIds);
    Set<String> protectedPublicIds =
        new HashSet<>(
            checkMarketplaceReservationCleanupProtectionPort.findProtectedExecutionIntentPublicIds(
                intents));
    return intents.stream()
        .filter(intent -> !protectedPublicIds.contains(intent.publicId()))
        .map(MarketplaceExecutionCleanupIntent::id)
        .toList();
  }
}
