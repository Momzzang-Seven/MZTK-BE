package momzzangseven.mztkbe.modules.web3.shared.infrastructure.adapter;

import java.math.BigInteger;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;

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
