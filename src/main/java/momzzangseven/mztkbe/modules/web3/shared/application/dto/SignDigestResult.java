package momzzangseven.mztkbe.modules.web3.shared.application.dto;

import java.util.Arrays;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;

/**
 * Result of a {@code SignDigestUseCase} invocation, carrying the canonical {@code (r, s, v)}
 * signature components.
 *
 * <p>{@code r} and {@code s} are exactly 32 bytes, big-endian, left-zero-padded; {@code v} is
 * either {@code 27} or {@code 28} (Ethereum convention). For raw EIP-1559 / EIP-7702 envelope
 * encoding, callers either translate to {@code yParity = v - 27} per design doc §6-1 / §6-2 or use
 * {@link #toCanonical65Bytes()} when the consumer expects the historical {@code r ‖ s ‖ v} 65-byte
 * concatenation.
 *
 * <p>{@code r} and {@code s} are defensively cloned at construction and at each accessor call, so
 * that mutations on a caller-held reference cannot retroactively alter the signature. Equality is
 * content-based.
 */
@SuppressWarnings("ArrayRecordComponent")
public record SignDigestResult(byte[] r, byte[] s, byte v) {

  private static final int COMPONENT_LENGTH = 32;
  private static final int CANONICAL_LENGTH = 65;

  /** Compact constructor — defensively clones the {@code r} / {@code s} arrays. */
  public SignDigestResult {
    if (r != null) {
      r = r.clone();
    }
    if (s != null) {
      s = s.clone();
    }
  }

  @Override
  public byte[] r() {
    return r.clone();
  }

  @Override
  public byte[] s() {
    return s.clone();
  }

  /**
   * Build a {@link SignDigestResult} from a domain {@link Vrs} value.
   *
   * @param vrs the signature components produced by {@code DerToVrsConverter}
   * @return a result instance carrying defensively-copied components
   */
  public static SignDigestResult from(Vrs vrs) {
    return new SignDigestResult(vrs.r(), vrs.s(), vrs.v());
  }

  /**
   * Serialize the signature into the canonical 65-byte {@code r ‖ s ‖ v} concatenation.
   *
   * <p>This format is consumed by the EIP-1559 / EIP-7702 transaction encoders that build the outer
   * transaction envelope (design doc §6-1).
   *
   * @return a freshly-allocated 65-byte array
   */
  public byte[] toCanonical65Bytes() {
    byte[] out = new byte[CANONICAL_LENGTH];
    System.arraycopy(r, 0, out, 0, COMPONENT_LENGTH);
    System.arraycopy(s, 0, out, COMPONENT_LENGTH, COMPONENT_LENGTH);
    out[CANONICAL_LENGTH - 1] = v;
    return out;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof SignDigestResult other)) {
      return false;
    }
    return v == other.v && Arrays.equals(r, other.r) && Arrays.equals(s, other.s);
  }

  @Override
  public int hashCode() {
    int result = Byte.hashCode(v);
    result = 31 * result + Arrays.hashCode(r);
    result = 31 * result + Arrays.hashCode(s);
    return result;
  }

  @Override
  public String toString() {
    return "SignDigestResult[r="
        + Arrays.toString(r)
        + ", s="
        + Arrays.toString(s)
        + ", v="
        + v
        + "]";
  }
}
