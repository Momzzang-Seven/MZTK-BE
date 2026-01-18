package momzzangseven.mztkbe.modules.level.application.port.out;

/**
 * Facade outbound port for loading policies.
 *
 * <p>Aggregates fine-grained policy load ports to reduce injection fragmentation while preserving
 * existing port contracts via interface inheritance.
 */
public interface PolicyPort
    extends LoadLevelPolicyPort, LoadLevelPoliciesPort, LoadXpPolicyPort, LoadXpPoliciesPort {}

