package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/**
 * Result carrier for {@link SignQnaServerSigPort#sign}.
 *
 * <p>{@code signedAt} is the epoch-second clock value used to assemble the digest, and is passed
 * verbatim into the contract calldata. {@code signatureBytes} is the canonical {@code (r ‖ s ‖ v)}
 * 65-byte signature. {@code signingInstant} is the raw {@link Instant} the adapter captured from
 * {@code appClock} at sign time — exposed so callers can derive other deadlines (e.g. {@code
 * expiresAt}) from the same instant the digest was assembled at, avoiding sub-second drift between
 * separate {@code appClock.now()} calls. Note: {@code signedAt = signingInstant.epochSecond −
 * signedAtSkewSeconds}, so the two are not interchangeable.
 *
 * <p>{@code signatureBytes} is defensively cloned both at construction and at accessor call time,
 * and {@link #equals}, {@link #hashCode}, {@link #toString} are overridden based on byte[] content
 * equality. (Same convention as {@code SignDigestResult}: defensive copy + content-equal
 * overrides.)
 */
@SuppressWarnings("ArrayRecordComponent")
public record QnaServerSigResult(long signedAt, byte[] signatureBytes, Instant signingInstant) {

  /** Compact constructor — defensively clones {@code signatureBytes}. */
  public QnaServerSigResult {
    signatureBytes = signatureBytes == null ? null : signatureBytes.clone();
  }

  /**
   * Convenience constructor for test fixtures that don't care about the raw {@link Instant}. Falls
   * back to {@code Instant.ofEpochSecond(signedAt)} so {@link #equals} stays deterministic.
   */
  public QnaServerSigResult(long signedAt, byte[] signatureBytes) {
    this(signedAt, signatureBytes, Instant.ofEpochSecond(signedAt));
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
    if (!(obj instanceof QnaServerSigResult other)) {
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
    return "QnaServerSigResult[signedAt="
        + signedAt
        + ", signatureBytes="
        + Arrays.toString(signatureBytes)
        + ", signingInstant="
        + signingInstant
        + "]";
  }
}
