package momzzangseven.mztkbe.modules.web3.shared.domain.vo;

import java.util.Locale;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.web3j.crypto.WalletUtils;

/** Canonical EVM address value object. Always stored as lower-case hex. */
public record EvmAddress(String value) {

  public EvmAddress {
    if (value == null || value.isBlank()) {
      throw new Web3InvalidInputException("EVM address is required");
    }
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    if (!WalletUtils.isValidAddress(normalized)) {
      throw new Web3InvalidInputException("invalid EVM address: " + value);
    }
    value = normalized;
  }

  public static EvmAddress of(String raw) {
    return new EvmAddress(raw);
  }

  @Override
  public String toString() {
    return value;
  }
}
