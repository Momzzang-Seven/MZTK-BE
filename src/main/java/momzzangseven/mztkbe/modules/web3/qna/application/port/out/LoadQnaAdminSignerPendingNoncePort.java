package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

public interface LoadQnaAdminSignerPendingNoncePort {

  long loadPendingNonce(String signerAddress);
}
