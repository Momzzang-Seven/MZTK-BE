package momzzangseven.mztkbe.modules.level.application.port.out;

import momzzangseven.mztkbe.modules.level.application.port.out.dto.RewardMztkCommand;
import momzzangseven.mztkbe.modules.level.application.port.out.dto.RewardMztkResult;

/** Outbound port for issuing MZTK rewards (implemented by web3 module). */
public interface RewardMztkPort {
  RewardMztkResult reward(RewardMztkCommand command);
}
