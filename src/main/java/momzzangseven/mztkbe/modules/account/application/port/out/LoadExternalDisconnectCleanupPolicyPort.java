package momzzangseven.mztkbe.modules.account.application.port.out;

/** Output policy port providing external disconnect cleanup configuration. */
public interface LoadExternalDisconnectCleanupPolicyPort {

  int getSuccessRetentionDays();

  int getFailedRetentionDays();
}
