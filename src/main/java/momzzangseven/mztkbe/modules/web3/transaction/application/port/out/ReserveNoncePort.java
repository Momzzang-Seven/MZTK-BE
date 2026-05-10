package momzzangseven.mztkbe.modules.web3.transaction.application.port.out;

/** Port for monotonic nonce reservation per sender address. */
public interface ReserveNoncePort {
  long reserveNextNonce(String fromAddress);

  /**
   * Compare-and-set release of a previously reserved nonce. Returns {@code true} when the cursor
   * was rolled back from {@code nonce + 1} to {@code nonce}; {@code false} when another reservation
   * has already advanced the cursor in between (the gap is unrecoverable here — the caller must
   * raise an alert so the operations team can intervene).
   */
  boolean releaseNonce(String fromAddress, long nonce);
}
