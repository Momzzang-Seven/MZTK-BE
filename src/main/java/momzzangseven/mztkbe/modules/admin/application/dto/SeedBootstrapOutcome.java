package momzzangseven.mztkbe.modules.admin.application.dto;

/**
 * Outcome of the seed bootstrap process.
 *
 * @param created whether new seed admins were created
 * @param seedCount number of seed admins created (0 if already bootstrapped)
 * @param deliveredVia delivery channel identifier (null if no-op)
 */
public record SeedBootstrapOutcome(boolean created, int seedCount, String deliveredVia) {}
