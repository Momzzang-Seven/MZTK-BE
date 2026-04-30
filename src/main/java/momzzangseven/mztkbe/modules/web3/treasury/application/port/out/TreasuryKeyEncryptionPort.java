package momzzangseven.mztkbe.modules.web3.treasury.application.port.out;

public interface TreasuryKeyEncryptionPort {

  String encrypt(String plaintext, String keyB64);

  String generateKeyB64();
}
