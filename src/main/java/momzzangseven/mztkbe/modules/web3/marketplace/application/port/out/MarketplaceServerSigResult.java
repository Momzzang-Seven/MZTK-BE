package momzzangseven.mztkbe.modules.web3.marketplace.application.port.out;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

@SuppressWarnings("ArrayRecordComponent")
public record MarketplaceServerSigResult(
    long signedAt, byte[] signatureBytes, Instant signingInstant) {

  public MarketplaceServerSigResult {
    signatureBytes = signatureBytes == null ? null : signatureBytes.clone();
  }

  @Override
  public byte[] signatureBytes() {
    return signatureBytes == null ? null : signatureBytes.clone();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof MarketplaceServerSigResult other)) {
      return false;
    }
    return signedAt == other.signedAt
        && Arrays.equals(signatureBytes, other.signatureBytes)
        && Objects.equals(signingInstant, other.signingInstant);
  }

  @Override
  public int hashCode() {
    return Objects.hash(signedAt, Arrays.hashCode(signatureBytes), signingInstant);
  }

  @Override
  public String toString() {
    return "MarketplaceServerSigResult[signedAt="
        + signedAt
        + ", signatureBytes="
        + (signatureBytes == null ? "<null>" : "<redacted len=" + signatureBytes.length + ">")
        + ", signingInstant="
        + signingInstant
        + "]";
  }
}
