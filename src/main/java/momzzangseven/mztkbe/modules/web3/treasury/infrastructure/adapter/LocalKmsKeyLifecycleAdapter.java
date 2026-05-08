package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.adapter;

import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Non-KMS stand-in for {@link KmsKeyLifecyclePort}. Treasury provisioning / disable / archive flows
 * require a real KMS key, so this adapter is intentionally inert: every method throws {@link
 * UnsupportedOperationException}, which surfaces as HTTP 500 if a call ever reaches it while {@code
 * web3.kms.enabled} is false / unset. Its sole purpose is to satisfy bean wiring for {@code
 * DisableTreasuryWalletService} / {@code ArchiveTreasuryWalletService} / {@code
 * ProvisionTreasuryKeyService} so the Spring context starts cleanly when KMS is opted out.
 */
@Component
@ConditionalOnProperty(name = "web3.kms.enabled", havingValue = "false", matchIfMissing = true)
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
  public void importKeyMaterial(String kmsKeyId, byte[] encryptedKeyMaterial, byte[] importToken) {
    throw unsupported("importKeyMaterial");
  }

  @Override
  public void createAlias(String alias, String kmsKeyId) {
    throw unsupported("createAlias");
  }

  @Override
  public void updateAlias(String alias, String newKmsKeyId) {
    throw unsupported("updateAlias");
  }

  @Override
  public KmsKeyState describeAliasTarget(String alias) {
    throw unsupported("describeAliasTarget");
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
        "KmsKeyLifecyclePort."
            + operation
            + " is not available if web3.kms.enabled is value of false");
  }
}
