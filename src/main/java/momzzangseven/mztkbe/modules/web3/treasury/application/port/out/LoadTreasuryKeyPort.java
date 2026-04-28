package momzzangseven.mztkbe.modules.web3.treasury.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;

/** Port for loading and decrypting treasury key material. */
public interface LoadTreasuryKeyPort {
  Optional<TreasuryKeyMaterial> loadByAlias(String walletAlias, String kekB64);

  record TreasuryKeyMaterial(String treasuryAddress, String privateKeyHex) {

    public TreasuryKeyMaterial {
      treasuryAddress = EvmAddress.of(treasuryAddress).value();
      privateKeyHex = normalizePrivateKeyHex(privateKeyHex);
      validate(privateKeyHex);
    }

    public static TreasuryKeyMaterial of(String treasuryAddress, String privateKeyHex) {
      return new TreasuryKeyMaterial(treasuryAddress, privateKeyHex);
    }

    private static String normalizePrivateKeyHex(String privateKeyHex) {
      if (privateKeyHex == null || privateKeyHex.isBlank()) {
        throw new Web3InvalidInputException("privateKeyHex is required");
      }
      String normalized = privateKeyHex.trim();
      if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
        normalized = normalized.substring(2);
      }
      return normalized;
    }

    private static void validate(String privateKeyHex) {
      if (!privateKeyHex.matches("^[0-9a-fA-F]{64}$")) {
        throw new Web3InvalidInputException("privateKeyHex must be 32-byte hex");
      }
    }
  }
}
