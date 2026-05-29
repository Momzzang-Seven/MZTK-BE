package momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce;

public interface SponsorNonceLockPort {

  void lock(long chainId, String fromAddress);
}
