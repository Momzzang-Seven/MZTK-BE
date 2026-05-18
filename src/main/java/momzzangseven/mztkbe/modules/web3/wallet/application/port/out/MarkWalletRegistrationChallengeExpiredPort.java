package momzzangseven.mztkbe.modules.web3.wallet.application.port.out;

public interface MarkWalletRegistrationChallengeExpiredPort {

  void markExpired(String nonce);
}
