package momzzangseven.mztkbe.modules.web3.token.application.port.out;

public interface SaveTreasuryKeyPort {

  void upsert(String walletAlias, String treasuryAddress, String treasuryPrivateKeyEncrypted);
}
