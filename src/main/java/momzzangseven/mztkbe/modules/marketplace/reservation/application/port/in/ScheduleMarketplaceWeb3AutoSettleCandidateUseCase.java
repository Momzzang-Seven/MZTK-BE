package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ScheduleMarketplaceWeb3AutoSettleCandidateCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ScheduleMarketplaceWeb3AutoSettleResult;

public interface ScheduleMarketplaceWeb3AutoSettleCandidateUseCase {

  ScheduleMarketplaceWeb3AutoSettleResult execute(
      ScheduleMarketplaceWeb3AutoSettleCandidateCommand command);
}
