package momzzangseven.mztkbe.modules.admin.application.dto;

/**
 * Command for recovery reseed.
 *
 * @param rawAnchor the raw anchor value from the request
 * @param sourceIp the source IP address
 */
public record RecoveryReseedCommand(String rawAnchor, String sourceIp) {}
