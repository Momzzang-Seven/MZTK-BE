package momzzangseven.mztkbe.modules.admin.application.dto;

/**
 * Result of a recovery reseed operation.
 *
 * @param newSeedCount the number of new seed accounts created
 * @param deliveredVia the delivery channel identifier
 */
public record RecoveryReseedResult(int newSeedCount, String deliveredVia) {}
