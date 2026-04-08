package momzzangseven.mztkbe.modules.account.application.port.out;

/** Output policy port providing external disconnect retry configuration. */
public interface LoadExternalDisconnectPolicyPort {

  int getBatchSize();

  int getMaxAttempts();

  long getInitialBackoff();

  long getMaxBackoff();
}
