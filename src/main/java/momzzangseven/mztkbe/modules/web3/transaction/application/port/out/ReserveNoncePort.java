package momzzangseven.mztkbe.modules.web3.transaction.application.port.out;

/** Port for monotonic nonce reservation per sender address. */
public interface ReserveNoncePort {
  long reserveNextNonce(String fromAddress);
}
