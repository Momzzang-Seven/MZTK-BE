package momzzangseven.mztkbe.modules.web3.shared.domain.vo;

import java.util.Locale;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.web3j.crypto.WalletUtils;

/** Canonical EVM address value object. Always stored as lower-case hex. */
public record EvmAddress(String value) {

  public EvmAddress {
    value = normalize(value);
    validate(value);
  }

  public static EvmAddress of(String raw) {
    return new EvmAddress(raw);
  }

  @Override
  public String toString() {
    return value;
  }

  private static String normalize(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new Web3InvalidInputException("EVM address is required");
    }
    return raw.trim().toLowerCase(Locale.ROOT);
  }

  private static void validate(String normalized) {
    if (!WalletUtils.isValidAddress(normalized)) {
      throw new Web3InvalidInputException("invalid EVM address: " + normalized);
    }
  }
}
