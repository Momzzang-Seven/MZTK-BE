package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.external.web3;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;

@Component
public class WalletApprovalCalldataEncoder {

  public static final BigInteger MAX_UINT256 =
      BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE);

  public String encodeApproveMax(String spenderAddress) {
    return encodeApprove(spenderAddress, MAX_UINT256);
  }

  public String encodeApprove(String spenderAddress, BigInteger amountWei) {
    Function approve =
        new Function(
            "approve",
            List.of(new Address(EvmAddress.of(spenderAddress).value()), new Uint256(amountWei)),
            Collections.emptyList());
    return FunctionEncoder.encode(approve);
  }
}
