package momzzangseven.mztkbe.modules.web3.shared.infrastructure.adapter;

import momzzangseven.mztkbe.modules.web3.shared.application.port.out.KmsSignerPort;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Non-production stub for {@link KmsSignerPort} that satisfies the Spring DI graph in {@code !prod}
 * profiles (local / dev / test / integration / E2E).
 *
 * <p>This commit (MOM-340 Commit 1-3) introduces the shared application services without yet
 * delivering signing infrastructure. The full BouncyCastle-based local signer — capable of
 * round-trip signing via an in-memory wallet alias map — arrives in Commit 1-4. Until then this
 * stub exists solely to keep the Spring context buildable for all non-prod test profiles. Any
 * caller that actually invokes {@link #signDigest(String, byte[], String)} before Commit 1-4 will
 * receive a deterministic {@link UnsupportedOperationException}.
 */
@Component
@Profile("!prod")
public class LocalEcSignerAdapter implements KmsSignerPort {

  @Override
  public Vrs signDigest(String kmsKeyId, byte[] digest, String expectedAddress) {
    throw new UnsupportedOperationException(
        "LocalEcSignerAdapter is a non-prod stub; full implementation arrives in MOM-340 Commit 1-4");
  }
}
