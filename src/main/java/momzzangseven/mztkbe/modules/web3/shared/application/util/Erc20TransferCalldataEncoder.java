package momzzangseven.mztkbe.modules.web3.shared.application.util;

import java.math.BigInteger;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;

/**
 * Stateless ERC-20 {@code transfer(address,uint256)} ABI calldata encoder.
 *
 * <p>Lives under {@code web3/shared/application/util/} — a cross-cutting helper allowed for sibling
 * web3 modules per ARCHITECTURE.md's shared-kernel exception, alongside {@code domain/vo/}, {@code
 * domain/crypto/}, and {@code application/dto/}. Keeping it out of {@code
 * web3/shared/infrastructure/} preserves the rule that infrastructure of one module is not imported
 * by another.
 */
public final class Erc20TransferCalldataEncoder {

  private Erc20TransferCalldataEncoder() {}

  public static String encodeTransferData(String toAddress, BigInteger amountWei) {
    EvmAddress normalizedToAddress = EvmAddress.of(toAddress);
    if (amountWei == null || amountWei.signum() < 0) {
      throw new Web3InvalidInputException("amountWei must be >= 0");
    }

    Function transfer =
        new Function(
            "transfer",
            List.of(new Address(normalizedToAddress.value()), new Uint256(amountWei)),
            List.of());
    return FunctionEncoder.encode(transfer);
  }
}
