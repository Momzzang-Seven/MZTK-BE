package momzzangseven.mztkbe.modules.web3.treasury.application.port.out;

import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;

/**
 * Treasury-owned out-port for describing a KMS key's lifecycle state.
 *
 * <p>Implemented by a bridging adapter that delegates to the {@code shared} module's {@code
 * DescribeKmsKeyUseCase}. The intermediary port keeps the dependency direction clean: treasury
 * services depend on this treasury-local port, the bridging adapter is the only piece coupled to
 * the shared module.
 */
public interface DescribeKmsKeyPort {

  /**
   * @param kmsKeyId fully-qualified KMS key id (or alias)
   * @return current lifecycle state of the key
   */
  KmsKeyState describe(String kmsKeyId);

  /**
   * Bypass the shared in-port's burst-absorber cache. Use ONLY from provisioning-recovery decision
   * paths.
   *
   * @param kmsKeyId fully-qualified KMS key id (or alias)
   * @return current lifecycle state of the key, freshly fetched
   */
  KmsKeyState describeFresh(String kmsKeyId);
}
