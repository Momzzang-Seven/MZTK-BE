package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.adapter;

import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Non-production stand-in for {@link KmsKeyLifecyclePort}. Treasury provisioning / disable /
 * archive flows require a real KMS key, so this adapter is intentionally inert: every method
 * throws {@link UnsupportedOperationException}, which surfaces as HTTP 500 if a non-prod call ever
 * reaches it. Its sole purpose is to satisfy bean wiring for {@code DisableTreasuryWalletService}
 * / {@code ArchiveTreasuryWalletService} / {@code ProvisionTreasuryKeyService} so the Spring
 * context starts cleanly in local / dev / test / E2E.
 */
@Component
@Profile("!prod")
public class LocalKmsKeyLifecycleAdapter implements KmsKeyLifecyclePort {

  @Override
  public String createKey() {
    throw unsupported("createKey");
  }

  @Override
  public ImportParams getParametersForImport(String kmsKeyId) {
    throw unsupported("getParametersForImport");
  }

  @Override
  public void importKeyMaterial(
      String kmsKeyId, byte[] encryptedKeyMaterial, byte[] importToken) {
    throw unsupported("importKeyMaterial");
  }

  @Override
  public void createAlias(String alias, String kmsKeyId) {
    throw unsupported("createAlias");
  }

  @Override
  public void disableKey(String kmsKeyId) {
    throw unsupported("disableKey");
  }

  @Override
  public void scheduleKeyDeletion(String kmsKeyId, int pendingWindowDays) {
    throw unsupported("scheduleKeyDeletion");
  }

  private static UnsupportedOperationException unsupported(String operation) {
    return new UnsupportedOperationException(
        "KmsKeyLifecyclePort." + operation + " is not available outside the prod profile");
  }
}
