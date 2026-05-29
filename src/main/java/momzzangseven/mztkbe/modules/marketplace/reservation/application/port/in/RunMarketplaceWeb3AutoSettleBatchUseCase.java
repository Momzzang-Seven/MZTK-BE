package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceWeb3AutoSettleScanCursor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RunMarketplaceWeb3AutoSettleBatchResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.MarketplaceWeb3AutoSettlePolicy;

public interface RunMarketplaceWeb3AutoSettleBatchUseCase {

  RunMarketplaceWeb3AutoSettleBatchResult runBatch(
      LocalDateTime now,
      MarketplaceWeb3AutoSettlePolicy policy,
      String schedulerRunId,
      MarketplaceWeb3AutoSettleScanCursor startCursor);
}
