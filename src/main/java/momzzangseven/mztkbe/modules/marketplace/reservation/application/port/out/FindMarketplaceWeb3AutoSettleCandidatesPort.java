package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceWeb3AutoSettleCandidate;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceWeb3AutoSettleScanCursor;

public interface FindMarketplaceWeb3AutoSettleCandidatesPort {

  List<MarketplaceWeb3AutoSettleCandidate> findCandidates(
      LocalDateTime now,
      LocalDateTime settleCutoff,
      MarketplaceWeb3AutoSettleScanCursor cursor,
      int scanSize);
}
