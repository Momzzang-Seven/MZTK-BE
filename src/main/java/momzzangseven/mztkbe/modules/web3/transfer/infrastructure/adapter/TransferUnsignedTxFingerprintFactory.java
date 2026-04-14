package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferUnsignedTxSnapshot;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

@Component
public class TransferUnsignedTxFingerprintFactory {

  public String compute(TransferUnsignedTxSnapshot snapshot) {
    String canonical =
        String.join(
            "|",
            Long.toString(snapshot.chainId()),
            normalizeAddress(snapshot.fromAddress()),
            normalizeAddress(snapshot.toAddress()),
            snapshot.value().toString(),
            normalizeHex(snapshot.data()),
            Long.toString(snapshot.nonce()),
            snapshot.gasLimit().toString(),
            snapshot.maxPriorityFeePerGas().toString(),
            snapshot.maxFeePerGas().toString());
    return Hash.sha3String(canonical);
  }

  private String normalizeAddress(String address) {
    if (address == null || address.isBlank()) {
      throw new Web3InvalidInputException("address is required");
    }
    return Numeric.prependHexPrefix(Numeric.cleanHexPrefix(address)).toLowerCase();
  }

  private String normalizeHex(String value) {
    if (value == null || value.isBlank()) {
      return "0x";
    }
    return Numeric.prependHexPrefix(Numeric.cleanHexPrefix(value)).toLowerCase();
  }
}
