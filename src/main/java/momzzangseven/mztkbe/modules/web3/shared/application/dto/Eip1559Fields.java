package momzzangseven.mztkbe.modules.web3.shared.application.dto;

import java.math.BigInteger;
import java.util.regex.Pattern;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;

/**
 * Validated EIP-1559 (Type-2) field set carried as a wire-format DTO between callers and the {@link
 * momzzangseven.mztkbe.modules.web3.shared.application.port.out.Eip1559TxCodecPort} codec.
 *
 * <p>Lives under {@code web3/shared/application/dto/} so sibling modules (transaction, execution,
 * ...) can compose this without importing a sub-module-private encoder type.
 *
 * @param chainId positive chain identifier
 * @param nonce sender nonce, {@code >= 0}
 * @param maxPriorityFeePerGas tip cap in wei, {@code > 0}
 * @param maxFeePerGas fee cap in wei, {@code > 0} and {@code >= maxPriorityFeePerGas}
 * @param gasLimit gas limit, {@code > 0}
 * @param to 0x-prefixed 20-byte recipient address (validated via {@link EvmAddress#of(String)})
 * @param value transferred wei, {@code >= 0}
 * @param data 0x-prefixed hex calldata; {@code "0x"} is allowed for an empty payload
 */
public record Eip1559Fields(
    long chainId,
    long nonce,
    BigInteger maxPriorityFeePerGas,
    BigInteger maxFeePerGas,
    BigInteger gasLimit,
    String to,
    BigInteger value,
    String data) {

  // 0x-prefixed hex with an even number of hex chars; "0x" alone permitted for empty calldata.
  private static final Pattern HEX_DATA_PATTERN = Pattern.compile("^0x([0-9a-fA-F]{2})*$");

  /** Compact constructor — enforces EIP-1559 field invariants. */
  public Eip1559Fields {
    if (chainId <= 0) {
      throw new Web3InvalidInputException("chainId must be positive");
    }
    if (nonce < 0) {
      throw new Web3InvalidInputException("nonce must be >= 0");
    }
    if (maxPriorityFeePerGas == null || maxPriorityFeePerGas.signum() <= 0) {
      throw new Web3InvalidInputException("maxPriorityFeePerGas must be > 0");
    }
    if (maxFeePerGas == null || maxFeePerGas.signum() <= 0) {
      throw new Web3InvalidInputException("maxFeePerGas must be > 0");
    }
    if (maxFeePerGas.compareTo(maxPriorityFeePerGas) < 0) {
      throw new Web3InvalidInputException("maxFeePerGas must be >= maxPriorityFeePerGas");
    }
    if (gasLimit == null || gasLimit.signum() <= 0) {
      throw new Web3InvalidInputException("gasLimit must be > 0");
    }
    if (to == null || to.isBlank()) {
      throw new Web3InvalidInputException("to is required");
    }
    EvmAddress.of(to);
    if (value == null || value.signum() < 0) {
      throw new Web3InvalidInputException("value must be >= 0");
    }
    if (data == null || !HEX_DATA_PATTERN.matcher(data).matches()) {
      throw new Web3InvalidInputException("data must be 0x-prefixed hex");
    }
  }
}
