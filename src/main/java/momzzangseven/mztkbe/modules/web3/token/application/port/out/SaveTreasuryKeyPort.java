package momzzangseven.mztkbe.modules.web3.token.application.port.out;

public interface SaveTreasuryKeyPort {

  void upsert(String treasuryAddress, String treasuryPrivateKeyEncrypted);
}
