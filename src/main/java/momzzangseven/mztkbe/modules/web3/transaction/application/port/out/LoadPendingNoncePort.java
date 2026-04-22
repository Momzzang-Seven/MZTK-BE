package momzzangseven.mztkbe.modules.web3.transaction.application.port.out;

public interface LoadPendingNoncePort {

  long loadPendingNonce(String fromAddress);
}
