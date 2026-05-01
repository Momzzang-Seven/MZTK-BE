package momzzangseven.mztkbe.modules.web3.shared.domain.crypto;

import java.util.Arrays;

/**
 * Canonical {@code (r, s, v)} representation of an Ethereum ECDSA signature.
 *
 * <p>Both {@code r} and {@code s} are exactly 32 bytes, big-endian, left-zero-padded. {@code v} is
 * either {@code 27} or {@code 28} per Ethereum convention; downstream EIP-1559 / EIP-7702 encoders
 * are responsible for translating to {@code yParity = v - 27} when serializing the typed
 * transaction envelope.
 *
 * <p>Instances of this record are produced by {@link DerToVrsConverter#convert(byte[], byte[],
 * String)} after DER decoding, EIP-2 low-s correction, and recovery-id determination.
 *
 * <p>The {@code r} and {@code s} arrays are defensively cloned at construction and at every
 * accessor call, so that mutations on a caller-held reference cannot retroactively alter the
 * signature payload. Equality is content-based ({@link Arrays#equals(byte[], byte[])}) rather than
 * reference-based, matching the immutability contract documented in CLAUDE.md.
 *
 * @param r 32-byte big-endian {@code r} component of the signature
 * @param s 32-byte big-endian {@code s} component of the signature (always low-s)
 * @param v Ethereum recovery identifier, {@code 27} or {@code 28}
 */
@SuppressWarnings("ArrayRecordComponent")
public record Vrs(byte[] r, byte[] s, byte v) {

  /** Compact constructor — defensively clones the {@code r} / {@code s} arrays. */
  public Vrs {
    r = r.clone();
    s = s.clone();
  }

  @Override
  public byte[] r() {
    return r.clone();
  }

  @Override
  public byte[] s() {
    return s.clone();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof Vrs other)) {
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
    return "Vrs[r=" + Arrays.toString(r) + ", s=" + Arrays.toString(s) + ", v=" + v + "]";
  }
}
