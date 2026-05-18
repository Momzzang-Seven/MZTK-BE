package momzzangseven.mztkbe.modules.web3.marketplace.application.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionCleanupIntent;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.in.FilterMarketplaceExecutionCleanupCandidatesUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.CheckMarketplaceReservationCleanupProtectionPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplaceExecutionCleanupIntentPort;

/** Protects finalized marketplace execution intents that still have local repair evidence. */
public class MarketplaceExecutionCleanupProtectionService
    implements FilterMarketplaceExecutionCleanupCandidatesUseCase {

  private final LoadMarketplaceExecutionCleanupIntentPort loadMarketplaceExecutionCleanupIntentPort;
  private final CheckMarketplaceReservationCleanupProtectionPort
      checkMarketplaceReservationCleanupProtectionPort;

  public MarketplaceExecutionCleanupProtectionService(
      LoadMarketplaceExecutionCleanupIntentPort loadMarketplaceExecutionCleanupIntentPort,
      CheckMarketplaceReservationCleanupProtectionPort
          checkMarketplaceReservationCleanupProtectionPort) {
    this.loadMarketplaceExecutionCleanupIntentPort = loadMarketplaceExecutionCleanupIntentPort;
    this.checkMarketplaceReservationCleanupProtectionPort =
        checkMarketplaceReservationCleanupProtectionPort;
  }

  @Override
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
