package momzzangseven.mztkbe.modules.level.application.port.out;

/** Outbound port for issuing MZTK rewards (implemented by web3 module). */
public interface RewardMztkPort {
  RewardMztkResult reward(RewardMztkCommand command);
}
