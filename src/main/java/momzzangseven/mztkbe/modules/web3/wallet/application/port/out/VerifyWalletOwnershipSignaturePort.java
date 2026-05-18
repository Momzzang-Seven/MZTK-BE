package momzzangseven.mztkbe.modules.web3.wallet.application.port.out;

public interface VerifyWalletOwnershipSignaturePort {

  boolean verify(String challengeMessage, String nonce, String signature, String expectedAddress);
}
