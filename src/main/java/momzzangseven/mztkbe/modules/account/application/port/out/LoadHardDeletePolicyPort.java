package momzzangseven.mztkbe.modules.account.application.port.out;

/** Output policy port providing hard-delete configuration. */
public interface LoadHardDeletePolicyPort {

  int getRetentionDays();

  int getBatchSize();
}
