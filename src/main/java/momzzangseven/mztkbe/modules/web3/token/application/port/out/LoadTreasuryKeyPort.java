package momzzangseven.mztkbe.modules.web3.token.application.port.out;

import java.util.Optional;

/** Port for loading and decrypting treasury key material. */
public interface LoadTreasuryKeyPort {
  Optional<TreasuryKeyMaterial> load();

  record TreasuryKeyMaterial(String treasuryAddress, String privateKeyHex) {}
}
