package momzzangseven.mztkbe.modules.level.application.port.out;

/**
 * Facade outbound port for user progress persistence.
 *
 * <p>Aggregates fine-grained ports to reduce injection fragmentation while preserving existing port
 * contracts via interface inheritance.
 */
public interface UserProgressPort extends LoadUserProgressPort, SaveUserProgressPort {}
