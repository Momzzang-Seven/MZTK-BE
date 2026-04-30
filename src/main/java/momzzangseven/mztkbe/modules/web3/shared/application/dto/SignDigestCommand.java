package momzzangseven.mztkbe.modules.web3.shared.application.dto;

import java.util.Arrays;

/**
 * Command carrying the input required to perform a single KMS-backed digest signing.
 *
 * <p>Field semantics:
 *
 * <ul>
 *   <li>{@code kmsKeyId} — fully-qualified KMS key id (or alias) that owns the secp256k1 private
 *       key.
 *   <li>{@code digest} — exactly 32 bytes; the result of {@code keccak256} over the EIP-1559 /
 *       EIP-7702 unsigned transaction payload.
 *   <li>{@code expectedAddress} — the Ethereum wallet address (e.g. {@code 0x…}) bound to this KMS
 *       key. The signing pipeline tests both {@code v=27} and {@code v=28} and selects the recovery
 *       id that yields this address; an unrecoverable signature triggers a {@link
 *       momzzangseven.mztkbe.global.error.web3.SignatureRecoveryException}.
 * </ul>
 *
 * <p>The {@code digest} array is defensively cloned at construction and at each accessor invocation
 * to preserve immutability — mirrors the pattern established by {@code Vrs}. Equality is
 * content-based.
 */
@SuppressWarnings("ArrayRecordComponent")
public record SignDigestCommand(String kmsKeyId, byte[] digest, String expectedAddress) {

  /** Length of an EIP-1559 / EIP-7702 keccak256 digest in bytes. */
  private static final int DIGEST_LENGTH = 32;

  /** Compact constructor — defensively clones the {@code digest} array. */
  public SignDigestCommand {
    if (digest != null) {
      digest = digest.clone();
    }
  }

  @Override
  public byte[] digest() {
    return digest.clone();
  }

  /**
   * Validate the command's input shape.
   *
   * <p>Throws {@link IllegalArgumentException} on null/blank {@code kmsKeyId}, on a {@code digest}
   * that is null or not exactly 32 bytes, or on null/blank {@code expectedAddress}. The global
   * exception handler maps this to HTTP 400.
   *
   * @throws IllegalArgumentException when any input is missing or malformed
   */
  public void validate() {
    if (kmsKeyId == null || kmsKeyId.isBlank()) {
      throw new IllegalArgumentException("kmsKeyId must not be null or blank");
    }
    if (digest == null) {
      throw new IllegalArgumentException("digest must not be null");
    }
    if (digest.length != DIGEST_LENGTH) {
      throw new IllegalArgumentException(
          "digest must be exactly " + DIGEST_LENGTH + " bytes, got " + digest.length);
    }
    if (expectedAddress == null || expectedAddress.isBlank()) {
      throw new IllegalArgumentException("expectedAddress must not be null or blank");
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof SignDigestCommand other)) {
      return false;
    }
    return java.util.Objects.equals(kmsKeyId, other.kmsKeyId)
        && Arrays.equals(digest, other.digest)
        && java.util.Objects.equals(expectedAddress, other.expectedAddress);
  }

  @Override
  public int hashCode() {
    int result = java.util.Objects.hashCode(kmsKeyId);
    result = 31 * result + Arrays.hashCode(digest);
    result = 31 * result + java.util.Objects.hashCode(expectedAddress);
    return result;
  }

  @Override
  public String toString() {
    return "SignDigestCommand[kmsKeyId="
        + kmsKeyId
        + ", digest="
        + Arrays.toString(digest)
        + ", expectedAddress="
        + expectedAddress
        + "]";
  }
}
