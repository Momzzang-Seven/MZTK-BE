package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

/** Port for monotonic nonce reservation per sender address. */
public interface ReserveNoncePort {
  long reserveNextNonce(String fromAddress);
}
