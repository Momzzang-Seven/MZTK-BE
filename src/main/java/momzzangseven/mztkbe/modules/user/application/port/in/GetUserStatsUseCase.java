package momzzangseven.mztkbe.modules.user.application.port.in;

import momzzangseven.mztkbe.modules.user.application.dto.GetUserStatsResult;

/** Inbound query use case for aggregated user statistics. */
public interface GetUserStatsUseCase {

  GetUserStatsResult execute();
}
