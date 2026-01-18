package momzzangseven.mztkbe.modules.level.application.port.out;

/**
 * Facade outbound port for level-up history.
 *
 * <p>Aggregates fine-grained ports to reduce injection fragmentation while preserving existing port
 * contracts via interface inheritance.
 */
public interface LevelUpHistoryPort
    extends LoadLevelUpHistoriesPort, SaveLevelUpHistoryPort, UpdateLevelUpHistoryRewardPort {}
